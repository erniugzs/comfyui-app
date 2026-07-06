package com.huilian.comfymobile.data.models

import com.google.gson.JsonObject

data class PromptRequest(
    val prompt: JsonObject,
    val client_id: String = "",
    val extra_data: JsonObject? = null
)
