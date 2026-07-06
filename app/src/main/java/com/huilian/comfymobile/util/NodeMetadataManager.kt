package com.huilian.comfymobile.util

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.huilian.comfymobile.data.ComfyUIService
import com.huilian.comfymobile.data.models.NodeTypeInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NodeMetadataManager(
    private val serviceProvider: () -> ComfyUIService
) {
    private val TAG = "NodeMetadataManager"
    private val cache = mutableMapOf<String, NodeTypeInfo>()
    private val mutex = Mutex()

    suspend fun getWidgetLabelsMap(nodeType: String): Map<Int, String> {
        val metadata = loadMetadata(nodeType) ?: return emptyMap()
        val result = mutableMapOf<Int, String>()
        var index = 0

        val inputOrder = metadata.input_order
        val inputInfo = metadata.input

        val orderedNames = mutableListOf<String>()
        inputOrder?.required?.let { orderedNames.addAll(it) }
        inputOrder?.optional?.let { orderedNames.addAll(it) }
        inputOrder?.hidden?.let { orderedNames.addAll(it) }

        if (orderedNames.isEmpty()) {
            inputInfo?.required?.keys?.let { orderedNames.addAll(it) }
            inputInfo?.optional?.keys?.let { orderedNames.addAll(it) }
        }

        for (name in orderedNames) {
            val paramInfo = inputInfo?.required?.get(name) ?: inputInfo?.optional?.get(name)
            if (paramInfo != null) {
                val options = paramInfo.second
                if (!options.forceInput) {
                    result[index] = name
                    index++
                }
            }
        }

        return result
    }

    suspend fun loadMetadata(nodeType: String): NodeTypeInfo? {
        mutex.withLock {
            cache[nodeType]?.let { return it }
        }

        return try {
            val service = serviceProvider()
            val response = service.getObjectInfo(nodeType)
            if (response.isSuccessful) {
                val body = response.body()
                val nodeJson = body?.get(nodeType)?.takeIf { it.isJsonObject }?.asJsonObject
                if (nodeJson != null) {
                    val info = NodeTypeInfo.fromJsonObject(nodeJson)
                    mutex.withLock {
                        cache[nodeType] = info
                    }
                    info
                } else {
                    null
                }
            } else {
                Log.w(TAG, "Failed to load metadata for $nodeType: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata for $nodeType", e)
            null
        }
    }

    fun getCached(nodeType: String): NodeTypeInfo? {
        return cache[nodeType]
    }

    fun clearCache() {
        cache.clear()
    }
}
