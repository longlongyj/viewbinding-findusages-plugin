package com.dragold.plugin

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression

/**
 * 修复 Java 代码调用 Kotlin companion object 中 @JvmStatic 方法时，
 * Ctrl+左键 / Go to Declaration 跳转到 .class 而不是 .kt 源码的问题。
 *
 * 原理：
 *  Kotlin 编译器对 companion object 中的 @JvmStatic 方法会生成两个 JVM 方法：
 *    1. Companion 内部类中带有实际实现的方法
 *    2. 外部类中委托给 Companion.INSTANCE.method() 的静态桥接方法
 *
 *  Java 代码调用 OuterClass.method() 时，IntelliJ 将引用解析到桥接方法（#2），
 *  该方法是 ClsMethodImpl，位于 .class 文件中，导致跳转到反编译视图而非源码。
 *
 *  本处理器在以下全部条件满足时才介入，其余情况一律交给平台默认处理：
 *   1. 在 Java 文件中
 *   2. 解析到的方法是静态方法（STATIC）
 *      注：不要求 PsiCompiledElement。Kotlin 模块以模块依赖存在时，
 *      Kotlin 插件生成的 light class 静态桥接方法不是 PsiCompiledElement，
 *      但仍会跳到 .class，同样需要修复。
 *   3. 该方法的 navigationElement 尚未正确指向 .kt / .java 源码（替代 PsiCompiledElement 检查）
 *   4. 外部类有 @kotlin.Metadata（确认是 Kotlin 生成类，排除纯 Java 类）
 *   5. 外部类有名为 Companion 的内部类
 *   6. Companion 中存在同名、同参数数量、且带 @JvmStatic 注解的方法
 *   7. 该方法的 navigationElement 确实指向 .kt 文件
 *
 *  不处理的场景（交给平台默认逻辑）：
 *   - DXIdv1Utils.Companion.getDeviceId()（显式 .Companion.：解析到实例方法，非 static，条件 2 不满足）
 *   - navigationElement 已能正确导航（条件 3 不满足）
 *   - 非 Kotlin 类（条件 4 不满足）
 *   - Companion 中无 @JvmStatic 的同名同参方法（条件 6 不满足，不做兜底 first() 回退）
 */
class JvmStaticGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // 条件 1：仅处理 Java 文件中的引用（不记录日志，避免每次 Ctrl+Click 都刷屏）
        if (sourceElement.containingFile !is PsiJavaFile) return null

        // 光标下叶节点的父节点通常是 PsiReferenceExpression
        val refExpr = sourceElement.parent as? PsiReferenceExpression ?: return null

        val resolved = refExpr.resolve() as? PsiMethod ?: return null

        // ── 到这里才开始记录，确认进入了目标路径 ─────────────────────────────────
        log("▶ entered: resolved='${resolved.name}' class=${resolved.javaClass.name}")

        // 条件 2：只关心静态方法
        // 注意：不再检查 PsiCompiledElement。
        //   当 Kotlin 模块以【模块依赖】形式存在（有源码）时，Kotlin 插件会生成
        //   light class（虚拟 PSI 类），其静态桥接方法不是 PsiCompiledElement，
        //   但 Ctrl+Click 仍会跳到 .class。PsiCompiledElement 检查会误将这类情况过滤掉。
        //   去掉后由 cond3（检查 navigationElement 是否已指向 .kt/.java）承担保护职责：
        //   若平台已能正确导航则直接跳过，不做多余干预。
        if (!resolved.hasModifierProperty(PsiModifier.STATIC)) {
            log("✗ cond2 FAIL: '${resolved.name}' is NOT static — skip")
            return null
        }
        log("✓ cond2: static (psiType=${resolved.javaClass.simpleName})")

        // 条件 3：若 resolved 的 navigationElement 已经指向源码（.kt / .java），无需介入
        val existingNav = resolved.navigationElement
        val existingNavSame = existingNav === resolved
        val existingExt = existingNav.containingFile?.virtualFile?.extension
        log("  cond3: existingNav same=$existingNavSame ext=$existingExt file=${existingNav.containingFile?.virtualFile?.path}")
        if (!existingNavSame && (existingExt == "kt" || existingExt == "java")) {
            log("✗ cond3 FAIL: navigationElement already points to source ('$existingExt') — skip")
            return null
        }
        log("✓ cond3: navigationElement not yet in source")

        val containingClass = resolved.containingClass ?: run {
            log("✗ containingClass is null — skip")
            return null
        }
        log("  containingClass='${containingClass.qualifiedName}' type=${containingClass.javaClass.name}")

        // 条件 4：外部类必须有 @kotlin.Metadata，确认是 Kotlin 编译产物
        val hasMetadata = containingClass.hasAnnotation("kotlin.Metadata")
        log("  cond4: @kotlin.Metadata present=$hasMetadata")
        if (!hasMetadata) {
            log("✗ cond4 FAIL: no @kotlin.Metadata on '${containingClass.qualifiedName}' — skip")
            return null
        }
        log("✓ cond4: Kotlin class confirmed")

        // 条件 5：外部类必须有名为 "Companion" 的内部类
        val companionClass = containingClass.findInnerClassByName("Companion", false)
        log("  cond5: Companion inner class found=${companionClass != null}")
        if (companionClass == null) {
            log("✗ cond5 FAIL: no inner class 'Companion' in '${containingClass.qualifiedName}' — skip")
            return null
        }
        log("✓ cond5: Companion='${companionClass.qualifiedName}'")

        val paramCount = resolved.parameterList.parametersCount
        val allCompanionMethods = companionClass.findMethodsByName(resolved.name, false)
        log("  cond6: looking for '${resolved.name}'($paramCount params) @JvmStatic in Companion")
        log("  cond6: found ${allCompanionMethods.size} method(s) named '${resolved.name}' in Companion:")
        allCompanionMethods.forEachIndexed { i, m ->
            val params = m.parameterList.parametersCount
            val hasJvmStatic = m.hasAnnotation("kotlin.jvm.JvmStatic")
            val annotations = m.annotations.joinToString { it.qualifiedName ?: "?" }
            log("    [$i] params=$params hasJvmStatic=$hasJvmStatic annotations=[$annotations]")
        }

        // 条件 6：Companion 中必须有同名、同参数数量、且带 @JvmStatic 注解的方法
        val companionMethod = allCompanionMethods.firstOrNull { method ->
            method.parameterList.parametersCount == paramCount &&
                    method.hasAnnotation("kotlin.jvm.JvmStatic")
        }
        if (companionMethod == null) {
            log("✗ cond6 FAIL: no @JvmStatic method matching '${resolved.name}'($paramCount params) in Companion — skip")
            return null
        }
        log("✓ cond6: matched companionMethod='${companionMethod.name}'")

        // 通过 navigationElement 跳转到 Kotlin 源码
        val navElement = companionMethod.navigationElement
        val navSame = navElement === companionMethod
        val navVFile = navElement.containingFile?.virtualFile
        log("  navElement same=$navSame type=${navElement.javaClass.name} file=${navVFile?.path} ext=${navVFile?.extension}")

        if (navSame) {
            log("✗ navigationElement === companionMethod (no source attached) — skip")
            return null
        }

        // 条件 7：navigationElement 必须确实指向 .kt 文件
        if (navVFile?.extension != "kt") {
            log("✗ cond7 FAIL: navigationElement ext=${navVFile?.extension} is not .kt — skip")
            return null
        }
        log("✓ cond7: .kt source confirmed → ${navVFile.name} offset=${navElement.textOffset}")

        log("★ redirecting '${resolved.name}'($paramCount params) → ${navVFile.name}:${navElement.textOffset}")
        return arrayOf(navElement)
    }

    companion object {
        // 使用 warn 级别，不受 LJLogger.ENABLED 开关影响，方便诊断问题
        // 确认问题已修复后，可将 warn 改回 info 并由 ENABLED 开关统一控制
        private fun log(msg: String) =
            LJLogger.debug(JvmStaticGotoDeclarationHandler::class.java, "[JvmStatic] $msg")
    }
}
