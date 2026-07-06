package com.huilian.comfymobile.data.models

import com.google.gson.JsonObject

data class NodeTypeInfo(
    val input: NodeInputInfo? = null,
    val input_order: NodeInputOrder? = null,
    val output: List<String> = emptyList(),
    val output_is_list: List<Boolean> = emptyList(),
    val output_name: List<String> = emptyList(),
    val name: String = "",
    val display_name: String = "",
    val description: String = "",
    val python_module: String = "",
    val category: String = "",
    val output_node: Boolean = false,
    val output_tooltips: List<String> = emptyList()
) {
    companion object {
        fun fromJsonObject(jsonObj: JsonObject): NodeTypeInfo {
            val input = jsonObj.getAsJsonObject("input")?.let { NodeInputInfo.fromJsonObject(it) }
            val inputOrder = jsonObj.getAsJsonObject("input_order")?.let { NodeInputOrder.fromJsonObject(it) }
            val output = jsonObj.getAsJsonArray("output")?.map { it.asString } ?: emptyList()
            val outputIsList = jsonObj.getAsJsonArray("output_is_list")?.map { it.asBoolean } ?: emptyList()
            val outputName = jsonObj.getAsJsonArray("output_name")?.map { it.asString } ?: emptyList()
            val name = jsonObj.get("name")?.asString ?: ""
            val displayName = jsonObj.get("display_name")?.asString ?: ""
            val description = jsonObj.get("description")?.asString ?: ""
            val pythonModule = jsonObj.get("python_module")?.asString ?: ""
            val category = jsonObj.get("category")?.asString ?: ""
            val outputNode = jsonObj.get("output_node")?.asBoolean ?: false
            val outputTooltips = jsonObj.getAsJsonArray("output_tooltips")?.map { it.asString } ?: emptyList()

            return NodeTypeInfo(
                input = input,
                input_order = inputOrder,
                output = output,
                output_is_list = outputIsList,
                output_name = outputName,
                name = name,
                display_name = displayName,
                description = description,
                python_module = pythonModule,
                category = category,
                output_node = outputNode,
                output_tooltips = outputTooltips
            )
        }
    }
}
