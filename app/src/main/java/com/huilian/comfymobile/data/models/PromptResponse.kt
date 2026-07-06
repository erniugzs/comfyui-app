package com.huilian.comfymobile.data.models

import com.google.gson.JsonObject

data class PromptResponse(
    val prompt_id: String = "",
    val number: Int = 0,
    val node_errors: JsonObject? = null
)
