package com.huilian.comfymobile.data.models

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class UiNode(
    val id: String,
    val type: String,
    val inputs: List<UiInput>,
    val outputs: List<UiOutput>,
    val widgets_values: JsonArray? = null,
    val widget_labels: Map<Int, String>? = null,
    val pos: JsonArray? = null,
    val size: JsonArray? = null,
    val flags: JsonObject? = null,
    val order: Int? = null,
    val mode: Int? = null,
    val properties: JsonObject? = null,
    val color: String? = null,
    val bgcolor: String? = null
)
