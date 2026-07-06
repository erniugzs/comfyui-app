package com.huilian.comfymobile.data.models

import com.google.gson.JsonObject

data class NodeInputOrder(
    val required: List<String> = emptyList(),
    val optional: List<String> = emptyList(),
    val hidden: List<String> = emptyList()
) {
    companion object {
        fun fromJsonObject(jsonObj: JsonObject): NodeInputOrder {
            val required = jsonObj.getAsJsonArray("required")?.map { it.asString } ?: emptyList()
            val optional = jsonObj.getAsJsonArray("optional")?.map { it.asString } ?: emptyList()
            val hidden = jsonObj.getAsJsonArray("hidden")?.map { it.asString } ?: emptyList()
            return NodeInputOrder(required, optional, hidden)
        }
    }
}
