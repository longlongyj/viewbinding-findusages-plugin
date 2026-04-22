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

        // ── 记录所有调用，无论类型 ──────────────────────────────────────────────
        log("[VBFindUsages] ReferencesSearcher.execute() elementType=${element.javaClass.simpleName} text='${element.text?.take(80)}'")

        // 入口4：LightDataBindingField —— 用户直接对 ViewBinding 字段做 Find Usages
        if (isLightDataBindingField(element)) {
            val fieldName = (element as? PsiNamedElement)?.name ?: run {
                log("[VBFindUsages] ReferencesSearcher: LightDataBindingField has no name — skipping")
                return true
            }
            log("[VBFindUsages] ReferencesSearcher: LightDataBindingField → fieldName='$fieldName'")
            contributeBindingFieldUsages(fieldName, params, element, consumer)
            return true
        }

        val rawId: String
        when {
            // 入口1：R.id PsiField（ResourceLightField 继承自 PsiField，编辑器高亮路径）
            element is PsiField && isRIdField(element) -> {
                rawId = element.name
                log("[VBFindUsages] ReferencesSearcher: R.id PsiField → rawId='$rawId' containingClass=${element.containingClass?.qualifiedName}")
            }
            // 入口2：XmlAttributeValue @+id/xxx 或 @id/xxx
            element is XmlAttributeValue &&
                    (element.value.startsWith("@+id/") || element.value.startsWith("@id/")) -> {
                rawId = IdToBindingFieldConverter.extractIdName(element.value)
                log("[VBFindUsages] ReferencesSearcher: XmlAttributeValue → rawId='$rawId'")
            }
            // 入口3：com.android.tools.idea.res.psi.ResourceReferencePsiElement
            isAndroidResourceReferenceElement(element) -> {
                val name = (element as? PsiNamedElement)?.name ?: ""
                rawId = name
                log("[VBFindUsages] ReferencesSearcher: ResourceReferencePsiElement → rawId='$rawId'")
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

        log("[VBFindUsages] ReferencesSearcher: rawId='$rawId' → bindingFieldName='$bindingFieldName'")
        contributeBindingFieldUsages(bindingFieldName, params, element, consumer)
        return true
    }

    /**
     * 在 [params] 的搜索范围内寻找所有 [bindingFieldName] 的 ViewBinding 访问，
     * 并通过 [consumer] 贡献 [ViewBindingPseudoReference]。
     */
    private fun contributeBindingFieldUsages(
        bindingFieldName: String,
        params: ReferencesSearch.SearchParameters,
        rIdElement: PsiElement,
        consumer: Processor<in PsiReference>
    ) {
        val scope = params.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.projectScope(rIdElement.project)

        val searchHelper = PsiSearchHelper.getInstance(rIdElement.project)
        var contributed = 0
        var skipped = 0
        searchHelper.processElementsWithWord(
            { psiElement, _ ->
                if (psiElement.text == bindingFieldName) {
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
            true // 大小写敏感
        )
        log("[VBFindUsages] ReferencesSearcher done: contributed=$contributed skipped=$skipped")
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
