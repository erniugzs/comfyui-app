package com.huilian.comfymobile.data.models

data class WorkflowMetadata(
    val id: String = "",
    val name: String = "",
    val filename: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val serverId: String? = null,
    val syncStatus: String = "local"
)
