package com.dragold.plugin

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.xml.XmlAttributeValue

/**
 * 处理两类入口：
 * 1. XmlAttributeValue（@+id/xxx）—— 非 Android Studio 环境或直接点击 XML 时
 * 2. R.id PsiField —— Android Studio 场景下 Android 插件将 XML id 转换为 R.id 字段后触发
 *    此时我们接管 handler，在 processElementUsages 里先调 super 保留原有 R.id/XML 结果，
 *    再额外搜索 ViewBinding 字段访问并直接 push UsageInfo，绕过 Android 插件的引用过滤。
 */
class ViewBindingFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean {
        if (element is XmlAttributeValue &&
            (element.value.startsWith("@+id/") || element.value.startsWith("@id/"))) {
            log("canFindUsages: XmlAttributeValue matched, text='${element.text?.take(60)}'")
            return true
        }
        if (element is PsiField && isRIdField(element)) {
            log("canFindUsages: R.id PsiField matched, name='${element.name}'")
            return true
        }
        return false
    }

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean
    ): FindUsagesHandler {
        log("createFindUsagesHandler for '${element.text?.take(60)}' forHighlightUsages=$forHighlightUsages elementType=${element.javaClass.simpleName}")
        return ViewBindingFindUsagesHandler(element)
    }

    private fun isRIdField(field: PsiField): Boolean {
        val containingClass = field.containingClass ?: return false
        if (containingClass.name == "id" && containingClass.containingClass?.name == "R") return true
        val qualifiedName = containingClass.qualifiedName ?: return false
        return qualifiedName.endsWith(".R.id") || qualifiedName.endsWith(".R\$id")
    }

    companion object {
        private fun log(msg: String) = LJLogger.info(ViewBindingFindUsagesHandlerFactory::class.java, "[VBFindUsages] $msg")
    }
}
