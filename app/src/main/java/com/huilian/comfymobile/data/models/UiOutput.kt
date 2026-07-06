package com.huilian.comfymobile.data.models

import com.google.gson.JsonElement

data class UiOutput(
    val name: String,
    val type: JsonElement,
    val links: List<Int>? = null,
    val slot_index: JsonElement? = null,
    val localizedName: String? = null,
    val shape: Int? = null,
    val widget: JsonElement? = null
)
