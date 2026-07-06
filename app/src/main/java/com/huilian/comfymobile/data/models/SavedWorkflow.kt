package com.huilian.comfymobile.data.models

data class SavedWorkflow(
    val id: String = "",
    val name: String = "",
    val json: String = "",
    val filename: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isStarred: Boolean = false,
    val serverId: String? = null,
    val source: String = "local"
)
