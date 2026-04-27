package com.dragold.plugin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.xml.XmlAttributeValue

/**
 * 将 ViewBinding 字段访问（如 binding.tvTitle 中的 tvTitle）包装为一个"伪引用",
 * resolve() 返回对应的 R.id 字段，使 Find Usages 面板能将其展示在结果里。
 */
class ViewBindingPseudoReference(
    usageElement: PsiElement,           // binding.tvTitle 中的 tvTitle 节点
    private val rIdField: PsiElement    // R.id.tv_title 字段
) : PsiReferenceBase<PsiElement>(usageElement, TextRange(0, usageElement.textLength), /* soft= */ true) {

    override fun resolve(): PsiElement = rIdField

    override fun getVariants(): Array<Any> = emptyArray()

    /**
     * Rename 时不做任何 PSI 修改。
     *
     * ViewBinding 字段（binding.tvTitle）由 Android 构建系统根据 XML id 自动生成，
     * 无需也不应该在 Rename 重构时手动修改它们（会在下次构建后自动更新）。
     *
     * 若不重写，父类默认实现会通过 ElementManipulator 修改 PSI，
     * 触发 PSI 变更事件 → ProcessesModel 通知 → AppInspectionView.updateUi()
     * 在 AWT 事件分发期间操作 UI 组件树，导致 IndexOutOfBoundsException。
     */
    override fun handleElementRename(newElementName: String): PsiElement {
        log("handleElementRename: no-op for ViewBinding pseudo reference, newName='$newElementName'")
        return element
    }

    /**
     * 检查本引用是否指向 [element]。
     * 加入了详细诊断日志，并扩展了多层 fallback 以应对 Android Studio 虚拟 R 字段。
     */
    override fun isReferenceTo(element: PsiElement): Boolean {
        val rIdFieldClass = rIdField.javaClass.simpleName
        val elementClass = element.javaClass.simpleName
        val rIdFieldName = (rIdField as? PsiNamedElement)?.name ?: "<no-name>"
        val elementName = (element as? PsiNamedElement)?.name ?: "<no-name>"

        val platformEquiv = element.manager.areElementsEquivalent(rIdField, element)
        if (platformEquiv) {
            log("isReferenceTo=true (areElementsEquivalent) rIdField=$rIdFieldClass/'$rIdFieldName' element=$elementClass/'$elementName'")
            return true
        }

        if (element is PsiField && rIdField is PsiField) {
            if (element.name == rIdField.name) {
                val eqn = element.containingClass?.qualifiedName ?: ""
                val tqn = rIdField.containingClass?.qualifiedName ?: ""
                val isRId = (eqn.endsWith(".R.id") || eqn.endsWith(".R\$id")) &&
                            (tqn.endsWith(".R.id") || tqn.endsWith(".R\$id"))
                log("isReferenceTo(PsiField fallback)=$isRId rIdField=$rIdFieldClass/'$rIdFieldName'(cls=$tqn) element=$elementClass/'$elementName'(cls=$eqn)")
                if (isRId) return true
            }
        }

        if (rIdField !is PsiField && element is PsiField) {
            val eqn = element.containingClass?.qualifiedName ?: ""
            val nameMatch = elementName == rIdFieldName
            val isRId = eqn.endsWith(".R.id") || eqn.endsWith(".R\$id")
            log("isReferenceTo(loose fallback A) nameMatch=$nameMatch isRId=$isRId rIdField=$rIdFieldClass/'$rIdFieldName' element=$elementClass/'$elementName'(cls=$eqn)")
            if (nameMatch && isRId) return true
        }

        if (isAndroidResourceReferenceElement(rIdField)) {
            val nameMatch = elementName == rIdFieldName && rIdFieldName.isNotEmpty() && rIdFieldName != "<no-name>"
            log("isReferenceTo(ResourceRef fallback) nameMatch=$nameMatch rIdField=$rIdFieldClass/'$rIdFieldName' element=$elementClass/'$elementName'")
            if (nameMatch) return true
        }

        if (isAndroidResourceReferenceElement(element)) {
            val nameMatch = elementName == rIdFieldName && rIdFieldName.isNotEmpty() && rIdFieldName != "<no-name>"
            log("isReferenceTo(element=ResourceRef fallback) nameMatch=$nameMatch rIdField=$rIdFieldClass/'$rIdFieldName' element=$elementClass/'$elementName'")
            if (nameMatch) return true
        }

        if (rIdField is XmlAttributeValue && element is PsiField) {
            val xmlId = rIdField.value.removePrefix("@+id/").removePrefix("@id/")
            val fieldName = element.name ?: ""
            val eqn = element.containingClass?.qualifiedName ?: ""
            val match = fieldName == xmlId && (eqn.endsWith(".R.id") || eqn.endsWith(".R\$id"))
            log("isReferenceTo(XmlAttr fallback)=$match xmlId='$xmlId' fieldName='$fieldName' cls='$eqn'")
            if (match) return true
        }

        if (rIdField is XmlAttributeValue && element is XmlAttributeValue) {
            val match = rIdField.value == element.value
            log("isReferenceTo(XmlAttr-XmlAttr fallback)=$match '${rIdField.value}' vs '${element.value}'")
            if (match) return true
        }

        if (isLightDataBindingField(rIdField) || isLightDataBindingField(element)) {
            val nameMatch = rIdFieldName.isNotEmpty() && rIdFieldName != "<no-name>" && rIdFieldName == elementName
            log("isReferenceTo(LightDataBindingField fallback) nameMatch=$nameMatch rIdField=$rIdFieldClass/'$rIdFieldName' element=$elementClass/'$elementName'")
            if (nameMatch) return true
        }

        val nameFallback = rIdFieldName.isNotEmpty() && rIdFieldName != "<no-name>" && rIdFieldName == elementName
        log("isReferenceTo=false (all fallbacks failed, nameFallback=$nameFallback) rIdField=$rIdFieldClass/'$rIdFieldName' element=$elementClass/'$elementName'")
        return nameFallback
    }

    companion object {
        private fun log(msg: String) = LJLogger.debug(ViewBindingPseudoReference::class.java, "[VBFindUsages] $msg")

        private fun isAndroidResourceReferenceElement(element: PsiElement): Boolean =
            element.javaClass.name == "com.android.tools.idea.res.psi.ResourceReferencePsiElement"

        private fun isLightDataBindingField(element: PsiElement): Boolean =
            element.javaClass.name == "com.android.tools.idea.databinding.psiclass.LightBindingClass\$LightDataBindingField"
    }
}
