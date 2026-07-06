package com.huilian.comfymobile.data.models

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class NodeInputInfo(
    val required: Map<String, Pair<String, ParamOptions>> = emptyMap(),
    val optional: Map<String, Pair<String, ParamOptions>> = emptyMap()
) {
    companion object {
        fun fromJsonObject(jsonObj: JsonObject): NodeInputInfo {
            val required = parseInputMap(jsonObj.getAsJsonObject("required"))
            val optional = parseInputMap(jsonObj.getAsJsonObject("optional"))
            return NodeInputInfo(required, optional)
        }

        private fun parseInputMap(jsonObj: JsonObject?): Map<String, Pair<String, ParamOptions>> {
            if (jsonObj == null) return emptyMap()
            val result = mutableMapOf<String, Pair<String, ParamOptions>>()
            for ((key, value) in jsonObj.entrySet()) {
                if (value.isJsonArray) {
                    val arr = value.asJsonArray
                    val typeStr = if (arr.size() > 0) arr.get(0)?.asString ?: "" else ""
                    val options = if (arr.size() > 1 && arr.get(1).isJsonObject) {
                        parseParamOptions(arr.get(1).asJsonObject)
                    } else {
                        ParamOptions()
                    }
                    result[key] = typeStr to options
                }
            }
            return result
        }

        private fun parseParamOptions(jsonObj: JsonObject): ParamOptions {
            return ParamOptions(
                type = jsonObj.get("type")?.asString ?: "",
                forceInput = jsonObj.get("forceInput")?.asBoolean ?: false,
                lazy = jsonObj.get("lazy")?.asBoolean ?: false,
                tooltip = jsonObj.get("tooltip")?.asString ?: "",
                category = jsonObj.get("category")?.asString ?: ""
            )
        }
    }
}
