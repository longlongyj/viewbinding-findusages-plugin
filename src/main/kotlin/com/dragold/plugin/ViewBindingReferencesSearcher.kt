package com.dragold.plugin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor

/**
 * ReferencesSearch 扩展：当 IntelliJ/Android Studio 搜索 R.id.xxx 的所有引用时，
 * 同时贡献对应 ViewBinding 字段（binding.tvTitle）的访问位置。
 *
 * 支持入口：
 *  1. PsiField（R.id.xxx 字段）
 *  2. XmlAttributeValue（@+id/xxx）
 *  3. ResourceReferencePsiElement（Android Studio Find 窗口路径）
 *  4. LightDataBindingField（直接对 ViewBinding 字段做 Find Usages）
 */
class ViewBindingReferencesSearcher
    : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

    override fun execute(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val element = params.elementToSearch

        // ── 快速跳过 C++/OC 相关元素（无需日志，避免噪音）────────────────────────
        val elementClassName = element.javaClass.name
        if (elementClassName.startsWith("com.jetbrains.cidr.") ||
            elementClassName.startsWith("com.jetbrains.objc.")) {
            return true
        }

        log("[VBFindUsages] ReferencesSearcher.execute() elementType=${element.javaClass.simpleName} text='${element.text?.take(80)}'")

        // 入口4：LightDataBindingField —— 用户在 Kotlin/Java 中对 ViewBinding 字段做 Find Usages
        //   containingClass 即为 Binding 类（如 FragmentHomeBinding），可精确过滤
        if (isLightDataBindingField(element)) {
            val fieldName = (element as? PsiNamedElement)?.name ?: run {
                log("[VBFindUsages] ReferencesSearcher: LightDataBindingField has no name — skipping")
                return true
            }
            val bindingClass = (element as? PsiField)?.containingClass?.name ?: ""
            log("[VBFindUsages] ReferencesSearcher: LightDataBindingField → fieldName='$fieldName' bindingClass='$bindingClass'")
            contributeBindingFieldUsages(fieldName, params, element, consumer, expectedBindingClass = bindingClass)
            return true
        }

        val rawId: String
        // expectedBindingClass：从来源 XML 文件名推导，用于过滤候选引用
        val expectedBindingClass: String
        when {
            // 入口1：R.id PsiField（ResourceLightField —— 编辑器高亮路径）
            //   R.id 字段不知道来自哪个 XML，无法确定 Binding 类，跳过贡献避免误报
            element is PsiField && isRIdField(element) -> {
                log("[VBFindUsages] ReferencesSearcher: R.id PsiField (editor highlight) — skipping to avoid false positives")
                return true
            }
            // 入口2：XmlAttributeValue @+id/xxx 或 @id/xxx
            element is XmlAttributeValue &&
                    (element.value.startsWith("@+id/") || element.value.startsWith("@id/")) -> {
                rawId = IdToBindingFieldConverter.extractIdName(element.value)
                // 从 XML 文件名推导 Binding 类名，如 fragment_home.xml → FragmentHomeBinding
                val xmlFileName = element.containingFile?.name?.removeSuffix(".xml") ?: ""
                expectedBindingClass = IdToBindingFieldConverter.toBindingClassName(xmlFileName)
                log("[VBFindUsages] ReferencesSearcher: XmlAttributeValue → rawId='$rawId' expectedBindingClass='$expectedBindingClass'")
            }
            // 入口3：com.android.tools.idea.res.psi.ResourceReferencePsiElement
            isAndroidResourceReferenceElement(element) -> {
                val name = (element as? PsiNamedElement)?.name ?: ""
                rawId = name
                // ResourceReferencePsiElement 的 containingFile 可能指向 XML 文件
                val xmlFileName = element.containingFile?.name?.removeSuffix(".xml") ?: ""
                expectedBindingClass = IdToBindingFieldConverter.toBindingClassName(xmlFileName)
                log("[VBFindUsages] ReferencesSearcher: ResourceReferencePsiElement → rawId='$rawId' expectedBindingClass='$expectedBindingClass'")
            }
            // 其他类型——静默跳过
            else -> {
                val name = (element as? PsiNamedElement)?.name
                log("[VBFindUsages] ReferencesSearcher: unsupported element type ${element.javaClass.name} name=$name — skipping")
                return true
            }
        }

        val bindingFieldName = IdToBindingFieldConverter.toBindingFieldName(rawId)
        if (bindingFieldName.isBlank()) {
            log("[VBFindUsages] ReferencesSearcher: bindingFieldName is blank for rawId='$rawId', skipping")
            return true
        }

        log("[VBFindUsages] ReferencesSearcher: rawId='$rawId' → bindingFieldName='$bindingFieldName' expectedBindingClass='$expectedBindingClass'")
        contributeBindingFieldUsages(bindingFieldName, params, element, consumer, expectedBindingClass)
        return true
    }

    /**
     * 在 [params] 的搜索范围内寻找所有 [bindingFieldName] 的 ViewBinding 访问，
     * 并通过 [consumer] 贡献 [ViewBindingPseudoReference]。
     *
     * @param expectedBindingClass 预期的 Binding 类名（如 FragmentHomeBinding）。
     *   非空时只贡献含有该类名的文件中的引用，避免同名 id 跨 XML 污染结果。
     *   空字符串表示不过滤。
     */
    private fun contributeBindingFieldUsages(
        bindingFieldName: String,
        params: ReferencesSearch.SearchParameters,
        rIdElement: PsiElement,
        consumer: Processor<in PsiReference>,
        expectedBindingClass: String
    ) {
        val scope = params.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.projectScope(rIdElement.project)

        val searchHelper = PsiSearchHelper.getInstance(rIdElement.project)
        var contributed = 0
        var skipped = 0
        var skippedClass = 0
        searchHelper.processElementsWithWord(
            { psiElement, _ ->
                if (psiElement.text == bindingFieldName) {
                    // ── 按 Binding 类名过滤：仅保留含目标 Binding 类的文件 ──────────
                    if (expectedBindingClass.isNotEmpty()) {
                        val fileText = psiElement.containingFile?.text ?: ""
                        if (!fileText.contains(expectedBindingClass)) {
                            skippedClass++
                            log("[VBFindUsages] skipped (bindingClass) #$skippedClass in '${psiElement.containingFile?.name}' — expected '$expectedBindingClass'")
                            return@processElementsWithWord true
                        }
                    }
                    // ── 按访问模式过滤 ──────────────────────────────────────────
                    if (isLikelyViewBindingAccess(psiElement)) {
                        contributed++
                        val accepted = consumer.process(ViewBindingPseudoReference(psiElement, rIdElement))
                        log("[VBFindUsages] contributing reference #$contributed: '${psiElement.containingFile?.name}' parent='${psiElement.parent?.text?.take(80)}' consumerAccepted=$accepted")
                    } else {
                        skipped++
                        log("[VBFindUsages] skipped (notBinding) #$skipped in '${psiElement.containingFile?.name}' parent='${psiElement.parent?.text?.take(80)}' parentType=${psiElement.parent?.javaClass?.simpleName}")
                    }
                }
                true
            },
            scope,
            bindingFieldName,
            UsageSearchContext.IN_CODE,
            true
        )
        log("[VBFindUsages] ReferencesSearcher done: contributed=$contributed skipped=$skipped skippedClass=$skippedClass")
    }

    // ── 工具方法 ────────────────────────────────────────────────────────────

    private fun isRIdField(field: PsiField): Boolean {
        val containingClass = field.containingClass ?: return false
        if (containingClass.name == "id" && containingClass.containingClass?.name == "R") return true
        val qualifiedName = containingClass.qualifiedName ?: return false
        return qualifiedName.endsWith(".R.id") || qualifiedName.endsWith(".R\$id")
    }

    private fun isAndroidResourceReferenceElement(element: PsiElement): Boolean {
        val className = element.javaClass.name
        return className == "com.android.tools.idea.res.psi.ResourceReferencePsiElement"
    }

    /**
     * 判断是否为 LightDataBindingField（ViewBinding 生成的虚拟字段，通过类名匹配）。
     * 类型：com.android.tools.idea.databinding.psiclass.LightBindingClass$LightDataBindingField
     */
    private fun isLightDataBindingField(element: PsiElement): Boolean {
        val className = element.javaClass.name
        return className == "com.android.tools.idea.databinding.psiclass.LightBindingClass\$LightDataBindingField"
    }

    private fun isLikelyViewBindingAccess(element: PsiElement): Boolean {
        val parentText = element.parent?.text ?: return false
        if (BINDING_ACCESS_PATTERN.containsMatchIn(parentText)) return true

        // Kotlin lambda PSI wraps: KtBlockExpression → KtFunctionLiteral → KtLambdaExpression
        // → KtLambdaArgument → KtCallExpression → KtDotQualifiedExpression(mBinding.let{...})
        // So we need depth ≥ 9 to reach the outer "mBinding.xxx {" expression.
        // Check BOTH patterns: scope-function pattern (exact) AND direct-access pattern
        // (covers the ancestor text that contains "mBinding." even without explicit scope fn).
        var ancestor = element.parent?.parent
        var depth = 0
        while (ancestor != null && depth < ANCESTOR_SEARCH_DEPTH) {
            val text = ancestor.text
            if (BINDING_ACCESS_PATTERN.containsMatchIn(text)) return true
            if (BINDING_SCOPE_FUNCTION_PATTERN.containsMatchIn(text)) return true
            ancestor = ancestor.parent
            depth++
        }
        return false
    }

    companion object {
        private fun log(msg: String) = LJLogger.info(ViewBindingReferencesSearcher::class.java, msg)
        private const val ANCESTOR_SEARCH_DEPTH = 12
        private val BINDING_ACCESS_PATTERN = Regex("""[a-zA-Z]*[Bb]inding\??\.""")
        private val BINDING_SCOPE_FUNCTION_PATTERN = Regex(
            """[a-zA-Z]*[Bb]inding\??\.(?:let|apply|run|also)\s*\{|with\s*\(\s*[a-zA-Z]*[Bb]inding"""
        )
    }
}
