package com.huilian.comfymobile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.huilian.comfymobile.data.*
import com.huilian.comfymobile.data.models.*
import com.huilian.comfymobile.util.NodeMetadataManager
import com.huilian.comfymobile.util.WorkflowJsonParser
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()
    private val prefs = application.getSharedPreferences("comfy_settings", Context.MODE_PRIVATE)
    private val workflowRepo = SavedWorkflowRepository(application)
    private val webSocketManager = WebSocketManager()
    private val networkScanner = NetworkScanner()

    private val nodeMetadataManager = NodeMetadataManager {
        RetrofitClient.getService(serverUrl.value)
    }
    private val workflowJsonParser = WorkflowJsonParser(nodeMetadataManager)

    // Server connection
    var serverUrl = mutableStateOf(prefs.getString("server_url", "http://127.0.0.1:8188/") ?: "http://127.0.0.1:8188/")
        private set
    var useHttps = mutableStateOf(prefs.getBoolean("use_https", false))
        private set
    var isConnected = mutableStateOf(false)
        private set
    var serverLatency = mutableStateOf(0L)
        private set
    var inFullscreenViewer = mutableStateOf(false)
    var serverStatus = mutableStateOf<JsonObject?>(null)
        private set

    // Workflows directory on server
    var workflowsDirectory = mutableStateOf(prefs.getString("workflows_directory", "workflows") ?: "workflows")
        private set

    // Server API key (optional, for auth-required servers)
    var serverApiKey = mutableStateOf(prefs.getString("server_api_key", "") ?: "")
   
    // Workflow
    var currentWorkflow = mutableStateOf<UiWorkflow?>(null)
        private set
    var editableWorkflow = mutableStateOf<List<EditableNode>>(emptyList())
        private set
    var savedWorkflows = mutableStateListOf<SavedWorkflow>()
    var workflowLoaded = mutableStateOf(false)
        private set
    var isLoading = mutableStateOf(false)
        private set
    var workflowParseError = mutableStateOf<String?>(null)
        private set
    var currentWorkflowSourceName = mutableStateOf<String?>(null)
        private set

    // Queue
    var queueItems = mutableStateOf<List<QueueItem>>(emptyList())
        private set
    var runningPrompts = mutableStateOf<Set<String>>(emptySet())
        private set
    var progressMap = mutableStateOf<Map<String, Float>>(emptyMap())
        private set
    var isRefreshing = mutableStateOf(false)
        private set
    var isGenerating = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf("")
        private set
    var queueStatus = mutableStateOf(QueueStatusResponse())
        private set

    // Server workflows
    var workflowFiles = mutableStateOf<List<String>>(emptyList())
        private set

    // Gallery
    var galleryImages = mutableStateListOf<String>()
    var galleryLoading = mutableStateOf(false)
        private set

    fun getImageUrl(fileName: String): String {
        return serverUrl.value.trimEnd('/') + "/view?filename=" +
                java.net.URLEncoder.encode(fileName, "UTF-8") +
                "&type=output&t=" + System.currentTimeMillis()
    }

    // Node metadata
    var nodeMetadata = mutableStateOf<JsonObject?>(null)
        private set
    var availableModels = mutableStateOf<List<String>>(emptyList())
        private set

    // Filter
    var filterPreferences = mutableStateOf(FilterPreferences())
        private set

    // Events
    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _webSocketEvents = MutableSharedFlow<WebSocketEvent>()
    val webSocketEvents = _webSocketEvents.asSharedFlow()

    private var pollJob: Job? = null
    private var wsJob: Job? = null

    init {
        viewModelScope.launch {
            loadSavedWorkflows()
            fetchNodeMetadata()
        }
    }

    // ==================== Server Connection ====================

    fun updateServerUrl(url: String) {
        serverUrl.value = url
        prefs.edit().putString("server_url", url).apply()
    }

    fun toggleHttps() {
        useHttps.value = !useHttps.value
        prefs.edit().putBoolean("use_https", useHttps.value).apply()
    }

    fun connectToServer() {
        viewModelScope.launch {
            try {
                val service = RetrofitClient.getService(serverUrl.value)
                val startTime = System.currentTimeMillis()
                val response = service.getSystemStats()
                val latency = System.currentTimeMillis() - startTime
                isConnected.value = response.isSuccessful
                serverLatency.value = if (response.isSuccessful) latency else 0L
                if (response.isSuccessful) {
                    serverStatus.value = response.body()
                    _snackbarEvents.emit("已连接到服务器 (${latency}ms)")
                    reconnectWebSocket()
                    refreshHistory()
                } else {
                    _snackbarEvents.emit("连接失败: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                isConnected.value = false
                serverLatency.value = 0L
                _snackbarEvents.emit("连接失败: ${e.message}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            webSocketManager.disconnect()
            isConnected.value = false
            wsJob?.cancel()
        }
    }

    fun scanForComfyUI(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            _snackbarEvents.emit("正在扫描网络...")
            val servers = networkScanner.scanForComfyUI()
            onResult(servers)
            _snackbarEvents.emit("发现 ${servers.size} 个服务器")
        }
    }

    // ==================== WebSocket ====================

    fun reconnectWebSocket() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            val wsUrl = serverUrl.value.replace("http://", "ws://").replace("https://", "wss://") + "ws"
            webSocketManager.connect(wsUrl)
            webSocketManager.events.collectLatest { event ->
                _webSocketEvents.emit(event)
                handleWebSocketEvent(event)
            }
        }
    }

    private fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event.type) {
            "progress" -> {
                val data = event.data?.jsonObject
                val value = data?.get("value")?.jsonPrimitive?.intOrNull ?: 0
                val max = data?.get("max")?.jsonPrimitive?.intOrNull ?: 1
                val progress = value.toFloat() / max.coerceAtLeast(1)
                val promptId = data?.get("prompt_id")?.jsonPrimitive?.content
                if (promptId != null) {
                    progressMap.value = progressMap.value + (promptId to progress)
                } else {
                    runningPrompts.value.firstOrNull()?.let {
                        progressMap.value = progressMap.value + (it to progress)
                    }
                }
            }
            "executed" -> {
                val promptId = event.data?.jsonObject?.get("prompt_id")?.jsonPrimitive?.content
                promptId?.let {
                    runningPrompts.value = runningPrompts.value - it
                    progressMap.value = progressMap.value - it
                }
                fetchGalleryImages()
            }
            "execution_error", "execution_interrupted" -> {
                val promptId = event.data?.jsonObject?.get("prompt_id")?.jsonPrimitive?.content
                promptId?.let {
                    runningPrompts.value = runningPrompts.value - it
                    progressMap.value = progressMap.value - it
                }
            }
            "execution_start" -> {
                val promptId = event.data?.jsonObject?.get("prompt_id")?.jsonPrimitive?.content
                promptId?.let {
                    runningPrompts.value = runningPrompts.value + it
                }
            }
        }
    }

    // ==================== Workflow ====================

    fun loadWorkflow(json: String, sourceName: String? = null) {
        viewModelScope.launch {
            try {
                isLoading.value = true
                workflowParseError.value = null
                val workflow = workflowJsonParser.parse(json)
                currentWorkflow.value = workflow
                editableWorkflow.value = workflowJsonParser.toEditableNodes(workflow)
                workflowLoaded.value = true
                if (sourceName != null) {
                    currentWorkflowSourceName.value = sourceName
                }
            } catch (e: Exception) {
                workflowParseError.value = "解析工作流失败: ${e.message}"
                _snackbarEvents.emit("解析工作流失败: ${e.message}")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadWorkflowFromSaved(saved: SavedWorkflow) {
        loadWorkflow(saved.json)
    }

    fun importWorkflowFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
                if (content.isBlank()) {
                    _snackbarEvents.emit("文件内容为空")
                    return@launch
                }
                val workflow = workflowJsonParser.parse(content)
                currentWorkflow.value = workflow
                editableWorkflow.value = workflowJsonParser.toEditableNodes(workflow)
                workflowLoaded.value = true
                _snackbarEvents.emit("工作流导入成功")
            } catch (e: Exception) {
                _snackbarEvents.emit("导入失败: ${e.message}")
            }
        }
    }

    fun updateEditableNode(nodeId: String, widgetLabel: String, value: String) {
        val list = editableWorkflow.value.toMutableList()
        val idx = list.indexOfFirst { it.id == nodeId }
        if (idx >= 0) {
            val node = list[idx]
            val widgets = node.widgets.toMutableList()
            val wIdx = widgets.indexOfFirst { it.label == widgetLabel }
            if (wIdx >= 0) {
                widgets[wIdx] = widgets[wIdx].copy(value = value)
                list[idx] = node.copy(widgets = widgets)
                editableWorkflow.value = list
            }
        }
    }

    fun saveCurrentWorkflow(name: String) {
        viewModelScope.launch {
            val wf = currentWorkflow.value ?: return@launch
            val json = workflowJsonParser.serialize(wf)
            val saved = SavedWorkflow(
                id = UUID.randomUUID().toString(),
                name = name,
                json = json,
                filename = "$name.json",
                source = "local"
            )
            workflowRepo.save(saved).onSuccess {
                savedWorkflows.add(0, it)
                _snackbarEvents.emit("工作流已保存")
            }.onFailure {
                _snackbarEvents.emit("保存失败: ${it.message}")
            }
        }
    }

    fun deleteSavedWorkflow(id: String) {
        viewModelScope.launch {
            workflowRepo.delete(id).onSuccess {
                savedWorkflows.removeAll { it.id == id }
                _snackbarEvents.emit("已删除")
            }
        }
    }

    fun renameSavedWorkflow(id: String, newName: String) {
        viewModelScope.launch {
            workflowRepo.rename(id, newName).onSuccess { updated ->
                val idx = savedWorkflows.indexOfFirst { it.id == id }
                if (idx >= 0) savedWorkflows[idx] = updated
                _snackbarEvents.emit("已重命名")
            }
        }
    }

    fun toggleStarWorkflow(id: String) {
        viewModelScope.launch {
            workflowRepo.toggleStar(id).onSuccess { updated ->
                val idx = savedWorkflows.indexOfFirst { it.id == id }
                if (idx >= 0) savedWorkflows[idx] = updated
            }
        }
    }

    private suspend fun loadSavedWorkflows() {
        val list = workflowRepo.loadAll().sortedByDescending { it.updatedAt }
        savedWorkflows.clear()
        savedWorkflows.addAll(list)
    }

    fun refreshSavedWorkflows() {
        viewModelScope.launch {
            loadSavedWorkflows()
        }
    }

    // ==================== Image Generation ====================

    fun generateImage() {
        viewModelScope.launch {
            try {
                isGenerating.value = true
                errorMessage.value = ""

                val wf = currentWorkflow.value ?: return@launch
                val apiJson = workflowJsonParser.toApiJson(wf, editableWorkflow.value)
                val promptObj = apiJson.getAsJsonObject("prompt")
                val request = PromptRequest(
                    prompt = promptObj,
                    client_id = UUID.randomUUID().toString()
                )

                val service = RetrofitClient.getService(serverUrl.value)
                val response = service.postPrompt(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    val promptId = body?.prompt_id
                    if (promptId != null && promptId.isNotBlank()) {
                        runningPrompts.value = runningPrompts.value + promptId
                        _snackbarEvents.emit("任务已提交")
                        launch { pollForCompletion(promptId) }
                        refreshHistory()
                    } else {
                        _snackbarEvents.emit("提交成功但缺少 prompt_id")
                    }
                } else {
                    _snackbarEvents.emit("提交失败: ${response.code()}")
                }
            } catch (e: Exception) {
                errorMessage.value = "生成失败: ${e.message}"
                _snackbarEvents.emit("生成失败: ${e.message}")
            } finally {
                isGenerating.value = false
            }
        }
    }

    suspend fun pollForCompletion(promptId: String) {
        repeat(60) {
            delay(5000L)
            if (!runningPrompts.value.contains(promptId)) return

            try {
                val service = RetrofitClient.getService(serverUrl.value)
                val response = withContext(Dispatchers.IO) {
                    service.getHistoryById(promptId)
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.has(promptId)) {
                        runningPrompts.value = runningPrompts.value - promptId
                        progressMap.value = progressMap.value - promptId
                        fetchGalleryImages()
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Poll check failed: ${e.message}")
            }
        }

        if (runningPrompts.value.contains(promptId)) {
            Log.w("MainViewModel", "Prompt $promptId timed out after 5 minutes, clearing")
            runningPrompts.value = runningPrompts.value - promptId
            progressMap.value = progressMap.value - promptId
        }
    }

    // ==================== Queue / History ====================

    fun refreshHistory() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                val service = RetrofitClient.getService(serverUrl.value)
                val response = withContext(Dispatchers.IO) {
                    service.getHistory(50)
                }

                if (response.isSuccessful) {
                    val body = response.body() ?: JsonObject()
                    val items = mutableListOf<QueueItem>()

                    body.entrySet().forEach { entry ->
                        val promptId = entry.key
                        val entryValue = entry.value
                        if (!entryValue.isJsonObject) return@forEach
                        val promptMap = entryValue.asJsonObject

                        val status = when {
                            promptMap.has("error") -> "error"
                            promptMap.getAsJsonObject("status")?.get("completed")?.asBoolean == true -> "completed"
                            else -> "running"
                        }

                        val imageUrl = extractFinalImageUrl(promptMap, serverUrl.value)
                        val imageInfo = extractImageInfo(promptMap)
                        val timestamp = extractTimestamp(promptMap)

                        items.add(QueueItem(promptId, status, imageUrl, timestamp, imageInfo.first, imageInfo.second))
                    }

                    items.filter { it.status != "running" }.forEach {
                        clearRunningPrompt(it.id)
                    }

                    val result = items.toMutableList()
                    runningPrompts.value.forEach { rpId ->
                        if (items.none { it.id == rpId }) {
                            result.add(QueueItem(rpId, "running", null, System.currentTimeMillis()))
                        }
                    }

                    result.sortByDescending { it.timestamp }
                    queueItems.value = result
                }
            } catch (e: Exception) {
                _snackbarEvents.emit("刷新历史失败: ${e.message}")
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun clearRunningPrompt(id: String) {
        runningPrompts.value = runningPrompts.value - id
    }

    fun deleteQueueTask(item: QueueItem) {
        viewModelScope.launch {
            try {
                // If running, interrupt the task first
                if (item.status == "running") {
                    withContext(Dispatchers.IO) {
                        val service = RetrofitClient.getService(serverUrl.value)
                        service.interruptTask()
                    }
                    runningPrompts.value = runningPrompts.value - item.id
                    progressMap.value = progressMap.value - item.id
                }

                // If completed, delete the generated image from server
                if (item.status == "completed") {
                    withContext(Dispatchers.IO) {
                        val service = RetrofitClient.getService(serverUrl.value)
                        val histResp = service.getHistoryById(item.id)
                        if (histResp.isSuccessful) {
                            val body = histResp.body() ?: JsonObject()
                            val outputs = body.getAsJsonObject("outputs") ?: JsonObject()
                            for (nodeEntry in outputs.entrySet()) {
                                val nodeValue = nodeEntry.value
                                if (!nodeValue.isJsonObject) continue
                                val images = nodeValue.asJsonObject.getAsJsonArray("images") ?: continue
                                for (imgElem in images) {
                                    if (!imgElem.isJsonObject) continue
                                    val filename = imgElem.asJsonObject.get("filename")?.asString ?: continue
                                    val subfolder = imgElem.asJsonObject.get("subfolder")?.asString ?: ""
                                    val type = imgElem.asJsonObject.get("type")?.asString ?: "output"
                                    // ComfyUI file delete: dir + filename
                                    val dir = if (subfolder.isNotEmpty()) "${type}/${subfolder}" else type
                                    val filePath = "$dir/$filename"
                                    try {
                                        service.deleteFile(filePath)
                                    } catch (e: Exception) {
                                        // Ignore individual file delete errors
                                    }
                                }
                            }
                        }
                    }
                }

                // Remove from local list
                queueItems.value = queueItems.value.filter { it.id != item.id }
                _snackbarEvents.emit("已删除任务")
                // Also refresh gallery to update image list
                fetchGalleryImages()
            } catch (e: Exception) {
                _snackbarEvents.emit("删除失败: ${e.message}")
            }
        }
    }

    fun getHistoryImageUrl(filename: String): String {
        return serverUrl.value.trimEnd('/') + "/view?filename=" +
                java.net.URLEncoder.encode(filename, "UTF-8") +
                "&type=output&t=" + System.currentTimeMillis()
    }

    fun fetchWorkflowForPromptId(promptId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val service = RetrofitClient.getService(serverUrl.value)
                val response = withContext(Dispatchers.IO) {
                    service.getHistoryById(promptId)
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    val promptEntry = body?.getAsJsonObject(promptId)
                    val promptData = promptEntry?.getAsJsonObject("prompt")
                    if (promptData != null) {
                        loadWorkflow(promptData.toString())
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    private fun extractFinalImageUrl(promptMap: JsonObject, serverUrl: String): String? {
        val outputs = promptMap.getAsJsonObject("outputs") ?: return null
        for (nodeEntry in outputs.entrySet()) {
            val nodeValue = nodeEntry.value
            if (!nodeValue.isJsonObject) continue
            val images = nodeValue.asJsonObject.getAsJsonArray("images") ?: continue
            if (images.size() == 0) continue
            val lastImage = images.last().asJsonObject
            val filename = lastImage.get("filename")?.asString ?: continue
            val subfolder = lastImage.get("subfolder")?.asString ?: ""
            val type = lastImage.get("type")?.asString ?: "output"

            val sb = StringBuilder()
            sb.append(serverUrl.trimEnd('/'))
            sb.append("/view?filename=")
            sb.append(java.net.URLEncoder.encode(filename, "UTF-8"))
            sb.append("&type=").append(type)
            sb.append("&t=").append(System.currentTimeMillis())
            if (subfolder.isNotEmpty()) {
                sb.append("&subfolder=").append(java.net.URLEncoder.encode(subfolder, "UTF-8"))
            }
            return sb.toString()
        }
        return null
    }

    private fun extractImageInfo(promptMap: JsonObject): Pair<String, String> {
        val outputs = promptMap.getAsJsonObject("outputs") ?: return Pair("", "")
        for (nodeEntry in outputs.entrySet()) {
            val nodeValue = nodeEntry.value
            if (!nodeValue.isJsonObject) continue
            val images = nodeValue.asJsonObject.getAsJsonArray("images") ?: continue
            if (images.size() == 0) continue
            val lastImage = images.last().asJsonObject
            val filename = lastImage.get("filename")?.asString ?: ""
            val width = lastImage.get("width")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            val height = lastImage.get("height")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            val name = filename.removeSuffix(".png").removeSuffix(".jpg").removeSuffix(".jpeg").removeSuffix(".webp")
            val size = if (width > 0 && height > 0) "${width}x${height}" else ""
            return Pair(name, size)
        }
        return Pair("", "")
    }

    private fun extractTimestamp(promptMap: JsonObject): Long {
        promptMap.get("timestamp")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.let {
            if (it.isNumber) return it.asNumber.toLong()
        }

        val statusObj = promptMap.getAsJsonObject("status") ?: return System.currentTimeMillis()
        val messages = statusObj.getAsJsonArray("messages") ?: return System.currentTimeMillis()
        val firstMessage = messages.firstOrNull() ?: return System.currentTimeMillis()
        if (!firstMessage.isJsonArray) return System.currentTimeMillis()
        val arr = firstMessage.asJsonArray
        if (arr.size() < 2) return System.currentTimeMillis()
        val data = arr.get(1)
        if (!data.isJsonObject) return System.currentTimeMillis()
        val ts = data.asJsonObject.get("timestamp")
        if (ts != null && ts.isJsonPrimitive && ts.asJsonPrimitive.isNumber) {
            return ts.asJsonPrimitive.asNumber.toLong()
        }
        return System.currentTimeMillis()
    }

    // ==================== Gallery ====================

    fun fetchGalleryImages() {
        viewModelScope.launch {
            galleryLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val service = RetrofitClient.getService(serverUrl.value)
                    val response = service.getHistory()
                    if (response.isSuccessful) {
                        val body = response.body() ?: JsonObject()
                        val allFilenames = mutableListOf<String>()

                        body.entrySet().forEach { entry ->
                            val entryValue = entry.value
                            if (!entryValue.isJsonObject) return@forEach
                            val outputsElem = entryValue.asJsonObject.get("outputs")
                            if (outputsElem == null || !outputsElem.isJsonObject) return@forEach
                            val outputs = outputsElem.asJsonObject
                            outputs.entrySet().forEach { nodeEntry ->
                                val nodeValue = nodeEntry.value
                                if (!nodeValue.isJsonObject) return@forEach
                                val imagesElem = nodeValue.asJsonObject.get("images")
                                if (imagesElem == null || !imagesElem.isJsonArray) return@forEach
                                imagesElem.asJsonArray.forEach { imgElem ->
                                    if (!imgElem.isJsonObject) return@forEach
                                    val filename = imgElem.asJsonObject.get("filename")?.asString ?: ""
                                    if (filename.isNotEmpty()) {
                                        allFilenames.add(filename)
                                    }
                                }
                            }
                        }

                        // Sort by timestamp extracted from ComfyUI_(\d+)_ pattern, descending
                        val sorted = allFilenames.distinct().sortedByDescending { name ->
                            val match = Regex("""ComfyUI_(\d+)_""").find(name)
                            match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        }

                        if (sorted.isNotEmpty()) {
                            galleryImages.clear()
                            galleryImages.addAll(sorted)
                        } else {
                            tryAlternativeImageSources()
                        }
                    } else {
                        tryAlternativeImageSources()
                    }
                }
            } catch (e: Exception) {
                _snackbarEvents.emit("获取图片失败: ${e.message}")
                tryAlternativeImageSources()
            } finally {
                galleryLoading.value = false
            }
        }
    }

    private suspend fun tryAlternativeImageSources() {
        withContext(Dispatchers.IO) {
            val service = RetrofitClient.getService(serverUrl.value)

            // Try output/comfyui
            val resp1 = service.getGalleryImages("output/comfyui")
            if (resp1.isSuccessful) {
                val files = resp1.body() ?: emptyList()
                val images = files.filter {
                    it.endsWith(".png", ignoreCase = true) || it.endsWith(".jpg", ignoreCase = true)
                }
                if (images.isNotEmpty()) {
                    galleryImages.clear()
                    galleryImages.addAll(images)
                    return@withContext
                }
            }

            // Try outputs
            val resp2 = service.getGalleryImages("outputs")
            if (resp2.isSuccessful) {
                val files = resp2.body() ?: emptyList()
                val images = files.filter {
                    it.endsWith(".png", ignoreCase = true) || it.endsWith(".jpg", ignoreCase = true)
                }
                if (images.isNotEmpty()) {
                    galleryImages.clear()
                    galleryImages.addAll(images)
                    return@withContext
                }
            }

            // Try ComfyUI
            val resp3 = service.getGalleryImages("ComfyUI")
            if (resp3.isSuccessful) {
                val files = resp3.body() ?: emptyList()
                val images = files.filter {
                    it.endsWith(".png", ignoreCase = true) ||
                    it.endsWith(".jpg", ignoreCase = true) ||
                    it.endsWith(".jpeg", ignoreCase = true) ||
                    it.endsWith(".webp", ignoreCase = true)
                }
                if (images.isNotEmpty()) {
                    galleryImages.clear()
                    galleryImages.addAll(images)
                    return@withContext
                }
            }

            if (galleryImages.isEmpty()) {
                _snackbarEvents.emit("无法找到任何图片，输出目录可能无法访问")
            }
        }
    }

    fun saveImageToGallery(context: Context, filename: String) {
        viewModelScope.launch {
            try {
                val imageUrl = getImageUrl(filename)
                withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false)
                        .build()
                    val result = Coil.imageLoader(context).execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val contentValues = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "ComfyUI_" + System.currentTimeMillis() + ".png")
                                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ComfyUI")
                                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                                }
                            }
                            val resolver = context.contentResolver
                            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { outputStream ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    contentValues.clear()
                                    contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                                    resolver.update(uri, contentValues, null, null)
                                }
                            }
                        }
                    }
                }
                _snackbarEvents.emit("已保存到相册")
            } catch (e: Exception) {
                e.printStackTrace()
                _snackbarEvents.emit("保存失败: ${e.message}")
            }
        }
    }

    fun shareImage(context: Context, filename: String) {
        viewModelScope.launch {
            try {
                val imageUrl = getImageUrl(filename)
                withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false)
                        .build()
                    val result = Coil.imageLoader(context).execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val cacheDir = File(context.cacheDir, "images")
                            cacheDir.mkdirs()
                            val file = File(cacheDir, "share_image.png")
                            FileOutputStream(file).use { fos ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                            }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                setDataAndType(uri, context.contentResolver.getType(uri))
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                type = "image/png"
                            }
                            withContext(Dispatchers.Main) {
                                context.startActivity(android.content.Intent.createChooser(intent, "分享图片"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _snackbarEvents.emit("分享失败: ${e.message}")
            }
        }
    }

    // ==================== Fetch Workflow from Server ====================

    fun fetchWorkflowFromServer(workflowName: String) {
        viewModelScope.launch {
            try {
                isLoading.value = true
                workflowParseError.value = null
                withContext(Dispatchers.IO) {
                    val service = RetrofitClient.getService(serverUrl.value)
                    val response = service.getWorkflow("${workflowsDirectory.value}/$workflowName")
                    if (response.isSuccessful) {
                        val body = response.body()
                        val json = body?.toString() ?: ""
                        if (json.isNotEmpty()) {
                            loadWorkflow(json, workflowName.removeSuffix(".json"))
                        } else {
                            _snackbarEvents.emit("工作流内容为空")
                        }
                    } else {
                        _snackbarEvents.emit("获取失败: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                workflowParseError.value = "获取工作流失败: ${e.message}"
                _snackbarEvents.emit("获取工作流失败: ${e.message}")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun fetchWorkflowFiles() {
        viewModelScope.launch {
            try {
                val service = RetrofitClient.getService(serverUrl.value)
                val response = service.getWorkflowList(workflowsDirectory.value)
                if (response.isSuccessful) {
                    val files = response.body() ?: emptyList()
                    workflowFiles.value = files
                } else {
                    _snackbarEvents.emit("获取列表失败: ${response.code()}")
                }
            } catch (e: Exception) {
                _snackbarEvents.emit("获取列表失败: ${e.message}")
            }
        }
    }

    // ==================== Node Metadata ====================

    fun fetchNodeMetadata() {
        viewModelScope.launch {
            try {
                val service = RetrofitClient.getService(serverUrl.value)
                val response = service.getAllObjectInfo()
                if (response.isSuccessful) {
                    nodeMetadata.value = response.body()
                }
            } catch (_: Exception) {}
        }
    }

    fun fetchAvailableModels() {
        viewModelScope.launch {
            try {
                val service = RetrofitClient.getService(serverUrl.value)
                val response = service.getObjectInfo("CheckpointLoaderSimple")
                if (response.isSuccessful) {
                    val body = response.body()
                    val models = mutableListOf<String>()
                    val ckptObj = body?.get("CheckpointLoaderSimple")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("input")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("required")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("ckpt_name")?.takeIf { it.isJsonArray }?.asJsonArray
                    ckptObj?.get(0)?.takeIf { it.isJsonArray }?.asJsonArray?.forEach {
                        if (it.isJsonPrimitive) models.add(it.asString)
                    }
                    availableModels.value = models
                }
            } catch (_: Exception) {}
        }
    }

    // ==================== Filter ====================

    fun updateFilterPreferences(prefs: FilterPreferences) {
        filterPreferences.value = prefs
    }

    fun getSerializedWorkflow(): String {
        return currentWorkflow.value?.let { workflowJsonParser.serialize(it) } ?: "{}"
    }

    suspend fun buildApiJson(): JsonObject? {
        val wf = currentWorkflow.value ?: return null
        return workflowJsonParser.toApiJson(wf, editableWorkflow.value)
    }

    fun buildApiPrompt(onResult: (JsonObject?) -> Unit) {
        viewModelScope.launch {
            onResult(buildApiJson())
        }
    }

    // ==================== Cleanup ====================

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        wsJob?.cancel()
        runBlocking { webSocketManager.disconnect() }
        networkScanner.cancel()
        RetrofitClient.reset()
    }
}
