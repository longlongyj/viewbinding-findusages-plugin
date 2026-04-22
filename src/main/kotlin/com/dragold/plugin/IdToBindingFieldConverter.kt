package com.dragold.plugin

/**
 * 将 XML resource id（snake_case）转换为 ViewBinding 字段名（camelCase）
 * 例如：container_tip_item_layout -> containerTipItemLayout
 */
object IdToBindingFieldConverter {

    fun toBindingFieldName(resourceId: String): String {
        if (resourceId.isBlank()) return resourceId
        return resourceId
            .split("_")
            .mapIndexed { index, part ->
                if (part.isEmpty()) return@mapIndexed ""
                if (index == 0) part.lowercase()
                else part.replaceFirstChar { it.uppercaseChar() }
            }
            .joinToString("")}

    /**
     * 从 XML 属性值中提取纯 id 名称
     * "@+id/container_tip_item_layout" -> "container_tip_item_layout"
     * "@id/container_tip_item_layout"  -> "container_tip_item_layout"
     */
    fun extractIdName(xmlAttributeValue: String): String {
        return xmlAttributeValue
            .removePrefix("@+id/")
            .removePrefix("@id/")
            .trim()
    }

    /**
     * 将 XML 文件名（不含扩展名）转换为 ViewBinding 类名
     * 例如：
     *   fragment_detail       -> FragmentDetailBinding
     *   activity_main         -> ActivityMainBinding
     *   item_container_layout -> ItemContainerLayoutBinding
     *
     * 规则：snake_case 各段首字母大写（PascalCase），末尾追加 "Binding"
     */
    fun toBindingClassName(xmlFileNameWithoutExtension: String): String {
        if (xmlFileNameWithoutExtension.isBlank()) return ""
        val pascal = xmlFileNameWithoutExtension
            .split("_")
            .joinToString("") { part ->
                if (part.isEmpty()) ""
                else part[0].uppercaseChar() + part.substring(1).lowercase()
            }
        return "${pascal}Binding"
    }
}
