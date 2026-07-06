package com.huilian.comfymobile.data.models

import com.google.gson.JsonArray
import com.google.gson.JsonElement

data class EditableNode(
    val id: String,
    val type: String,
    val inputs: List<EditableInput> = emptyList(),
    val widgets: List<EditableWidget> = emptyList(),
    val outputs: List<EditableOutput> = emptyList()
) {
    companion object {
        fun fromUiNode(node: UiNode): EditableNode {
            val inputs = mutableListOf<EditableInput>()
            val widgets = mutableListOf<EditableWidget>()
            val outputs = mutableListOf<EditableOutput>()

            // Parse inputs and extract widget values from unlinked inputs
            var inputIndex = 0
            val unlinkedInputs = node.inputs.filter { input ->
                val link = input.link
                link == null || link.isJsonNull
            }

            for (uiInput in unlinkedInputs) {
                val widgetValue = uiInput.widget
                var widgetStr: String? = null
                if (widgetValue != null && !widgetValue.isJsonNull && widgetValue.isJsonPrimitive) {
                    widgetStr = widgetValue.asString
                }

                val slotIndex = uiInput.slot_index
                val intValue = if (slotIndex != null && !slotIndex.isJsonNull) {
                    slotIndex.asInt
                } else {
                    inputIndex
                }

                val widgetsValues = node.widgets_values
                var valueAt: String? = getWidgetValueAt(widgetsValues, intValue)
                if (valueAt == null && widgetStr != null) {
                    valueAt = widgetStr
                }

                inputs.add(EditableInput(
                    name = uiInput.name,
                    type = if (uiInput.type.isJsonPrimitive) uiInput.type.asString else uiInput.type.toString(),
                    label = uiInput.localizedName,
                    link = if (uiInput.link != null && !uiInput.link.isJsonNull && uiInput.link.isJsonPrimitive && uiInput.link.asJsonPrimitive.isNumber) {
                        uiInput.link.asInt
                    } else null,
                    slotIndex = intValue,
                    value = valueAt ?: ""
                ))
                inputIndex++
            }

            // Parse widgets from widgets_values
            val widgetLabels = node.widget_labels ?: emptyMap()
            val widgetsValues = node.widgets_values
            if (widgetsValues != null) {
                var widgetIndex = 0
                for (jsonElement in widgetsValues) {
                    val label = getLabelAt(widgetLabels, widgetIndex)
                    val strValue = when {
                        jsonElement.isJsonPrimitive -> {
                            val primitive = jsonElement.asJsonPrimitive
                            when {
                                primitive.isString -> primitive.asString
                                primitive.isNumber -> primitive.asString
                                primitive.isBoolean -> primitive.asString
                                else -> jsonElement.toString()
                            }
                        }
                        else -> jsonElement.toString()
                    }
                    widgets.add(EditableWidget(label = label, value = strValue))
                    widgetIndex++
                }
            }

            // Parse outputs
            for (uiOutput in node.outputs) {
                outputs.add(EditableOutput(
                    name = uiOutput.name,
                    type = if (uiOutput.type.isJsonPrimitive) uiOutput.type.asString else uiOutput.type.toString(),
                    label = uiOutput.localizedName,
                    links = uiOutput.links?.map { it } ?: emptyList(),
                    slotIndex = uiOutput.slot_index?.asInt
                ))
            }

            return EditableNode(
                id = node.id,
                type = node.type,
                inputs = inputs,
                widgets = widgets,
                outputs = outputs
            )
        }

        private fun getWidgetValueAt(widgetsValues: JsonArray?, index: Int): String? {
            if (widgetsValues == null || index < 0 || index >= widgetsValues.size()) {
                return null
            }
            val element = widgetsValues[index]
            if (element == null || element.isJsonNull) {
                return null
            }
            if (element.isJsonPrimitive) {
                val primitive = element.asJsonPrimitive
                return when {
                    primitive.isString -> primitive.asString
                    primitive.isNumber -> primitive.asString
                    primitive.isBoolean -> primitive.asString
                    else -> element.toString()
                }
            }
            return element.toString()
        }

        private fun getLabelAt(widgetLabels: Map<Int, String>?, index: Int): String {
            return widgetLabels?.get(index) ?: "Widget #$index"
        }
    }
}

data class EditableInput(
    val name: String,
    val type: String,
    val label: String? = null,
    val link: Int? = null,
    val slotIndex: Int? = null,
    val value: String = ""
)

data class EditableWidget(
    val label: String,
    val value: String,
    val type: String = "STRING",
    val options: List<String> = emptyList()
)

data class EditableOutput(
    val name: String,
    val type: String,
    val label: String? = null,
    val links: List<Int> = emptyList(),
    val slotIndex: Int? = null
)
