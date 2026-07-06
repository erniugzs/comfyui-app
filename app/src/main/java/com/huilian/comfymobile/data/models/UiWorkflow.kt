package com.huilian.comfymobile.data.models

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class UiWorkflow(
    val version: Int,
    val nodes: List<UiNode>,
    val links: List<UiLink>,
    val groups: JsonArray? = null,
    val config: JsonObject? = null,
    val extra: JsonObject? = null,
    val definitions: JsonObject? = null,
    val lastNodeId: Int? = null,
    val lastLinkId: Int? = null
)
