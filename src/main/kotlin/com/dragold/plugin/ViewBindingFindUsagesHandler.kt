package com.dragold.plugin

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * 自定义 Find Usages 处理器。
 *
 * 对于编辑器高亮路径（forHighlightUsages=true），直接调用 super.processElementUsages 即可；
 * ViewBindingReferencesSearcher 作为 referencesSearch 扩展会在 super 触发的 ReferencesSearch
 * 中自动运行，贡献 ViewBinding 字段的访问位置，无需在此处重复搜索。
 */
class ViewBindingFindUsagesHandler(element: PsiElement) : FindUsagesHandler(element) {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        log("processElementUsages CALLED, elementType=${element.javaClass.simpleName} name='${element.text?.take(80)}'")
        return super.processElementUsages(element, processor, options)
    }

    companion object {
        private fun log(msg: String) = LJLogger.info(ViewBindingFindUsagesHandler::class.java, "[VBFindUsages] $msg")
    }
}
