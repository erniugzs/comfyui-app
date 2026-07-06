package com.huilian.comfymobile.data.models

import com.google.gson.JsonElement

data class UiInput(
    val name: String,
    val type: JsonElement,
    val link: JsonElement? = null,
    val slot_index: JsonElement? = null,
    val localizedName: String? = null,
    val shape: Int? = null,
    val widget: JsonElement? = null
)
