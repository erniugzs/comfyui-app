package com.huilian.comfymobile.data.models

data class QueueItem(
    val id: String,
    val status: String,
    val imageUrl: String?,
    val timestamp: Long,
    val imageName: String = "",
    val imageSize: String = ""
)
