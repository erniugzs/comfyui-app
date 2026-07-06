package com.huilian.comfymobile.data.models

data class QueueStatusResponse(
    val queue_running: List<List<Any>> = emptyList(),
    val queue_pending: List<List<Any>> = emptyList(),
    val queue_remaining: Int = 0
)
