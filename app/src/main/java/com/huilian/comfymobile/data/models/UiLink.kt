package com.huilian.comfymobile.data.models

data class UiLink(
    val id: Int,
    val origin_id: String,
    val origin_slot: Int,
    val target_id: String,
    val target_slot: Int,
    val type: String? = null
)
