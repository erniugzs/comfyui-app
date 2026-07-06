package com.huilian.comfymobile.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.huilian.comfymobile.MainViewModel
import com.huilian.comfymobile.components.RawJsonEditor
import com.huilian.comfymobile.components.SaveWorkflowDialog
import com.huilian.comfymobile.data.models.EditableNode
import com.huilian.comfymobile.data.models.EditableWidget
import com.huilian.comfymobile.data.models.QueueItem
import com.huilian.comfymobile.util.ComfyUILocalization
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowScreen(
    viewModel: MainViewModel,
    onGenerate: (JsonObject) -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isLoading = viewModel.isLoading.value
    val workflowLoaded = viewModel.workflowLoaded.value
    val editableNodes = viewModel.editableWorkflow.value
    val parseError = viewModel.workflowParseError.value
    val serverUrl = viewModel.serverUrl.value

    var showSaveDialog by remember { mutableStateOf(false) }
    var showRawJson by remember { mutableStateOf(false) }

    val isGenerating = viewModel.runningPrompts.value.isNotEmpty()
    val progress = viewModel.progressMap.value.values.firstOrNull() ?: 0f

    // 子状态返回：JSON编辑器/保存对话框优先，否则让上层处理
    BackHandler(enabled = showRawJson || showSaveDialog) {
        if (showRawJson) {
            showRawJson = false
        } else if (showSaveDialog) {
            showSaveDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "工作流编辑器",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (workflowLoaded) {
                            Text(
                                "${editableNodes.size} 个节点已加载",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (workflowLoaded) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                        IconButton(onClick = { showRawJson = true }) {
                            Icon(Icons.Default.Code, contentDescription = "JSON")
                        }
                        IconButton(onClick = {
                            viewModel.currentWorkflow.value?.let {
                                viewModel.loadWorkflow(viewModel.getSerializedWorkflow())
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (workflowLoaded) {
                WorkflowOutputPanel(
                    serverUrl = serverUrl,
                    isGenerating = isGenerating,
                    progress = progress,
                    onGenerate = {
                        scope.launch {
                            viewModel.generateImage()
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                isLoading -> LoadingScreen()
                parseError != null && parseError.isNotEmpty() -> ErrorScreen(parseError)
                !workflowLoaded -> EmptyWorkflowState()
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // 工作流摘要横幅
                        item(key = "summary") {
                            WorkflowSummaryBanner(
                                totalNodes = editableNodes.size,
                                showingNodes = editableNodes.size
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        items(editableNodes, key = { it.id }) { node ->
                            var isExpanded by remember(node.id) { mutableStateOf(false) }
                            NodeCard(
                                node = node,
                                isExpanded = isExpanded,
                                onExpandChange = { isExpanded = it },
                                onNodeChange = { updatedNode ->
                                    val list = editableNodes.toMutableList()
                                    val idx = list.indexOfFirst { it.id == updatedNode.id }
                                    if (idx >= 0) {
                                        list[idx] = updatedNode
                                        viewModel.editableWorkflow.value = list
                                    }
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveWorkflowDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCurrentWorkflow(name)
                showSaveDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("工作流已保存")
                }
            }
        )
    }

    if (showRawJson && workflowLoaded) {
        val currentJson = viewModel.getSerializedWorkflow()
        RawJsonEditor(
            initialJson = currentJson,
            onDismiss = { showRawJson = false },
            onSave = { json ->
                viewModel.loadWorkflow(json)
                showRawJson = false
            }
        )
    }
}

/**
 * 工作流摘要横幅 - 蓝色渐变背景
 */
@Composable
fun WorkflowSummaryBanner(
    totalNodes: Int,
    showingNodes: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF2196F3).copy(alpha = 0.15f),
                            Color(0xFF1976D2).copy(alpha = 0.25f),
                            Color(0xFF1565C0).copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "工作流概览",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1565C0),
                        fontSize = 14.sp
                    )
                    Text(
                        "显示 $showingNodes / $totalNodes 个节点",
                        color = Color(0xFF1976D2).copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("正在加载工作流...", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ErrorScreen(errorMsg: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("加载失败", style = MaterialTheme.typography.titleLarge)
            Text(
                errorMsg,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyWorkflowState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无工作流", color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * 节点颜色映射 - 按原版颜色方案
 */
@Composable
fun getNodeColor(nodeType: String): Color {
    return when {
        nodeType.contains("Checkpoint", true) || nodeType.contains("Lora", true) || nodeType.contains("VAELoader", true) ->
            Color(0xFFE91E63) // 粉红 - 模型加载器
        nodeType.contains("CLIPLoader", true) || nodeType.contains("CLIPSet", true) ->
            Color(0xFF9E9E9E) // 灰色 - CLIP相关
        nodeType.contains("VAE", true) ->
            Color(0xFF9C27B0) // 紫色 - VAE
        nodeType.contains("CLIPText", true) || nodeType.contains("Conditioning", true) ->
            Color(0xFFFF9800) // 橙色 - 文本编码
        nodeType.contains("KSampler", true) || nodeType.contains("Sampler", true) ->
            Color(0xFF2196F3) // 蓝色 - 采样器
        nodeType.contains("SaveImage", true) || nodeType.contains("Preview", true) ->
            Color(0xFF4CAF50) // 绿色 - 图像输出
        nodeType.contains("Empty", true) || nodeType.contains("Latent", true) ->
            Color(0xFF4CAF50) // 绿色 - Latent
        nodeType.contains("LoadImage", true) ->
            Color(0xFF00BCD4) // 青色 - 加载图像
        nodeType.contains("ControlNet", true) ->
            Color(0xFFFFEB3B) // 黄色 - ControlNet
        else -> Color(0xFF6750A4) // 默认紫色
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeCard(
    node: EditableNode,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onNodeChange: (EditableNode) -> Unit,
    viewModel: MainViewModel
) {
    val nodeColor = getNodeColor(node.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧彩色边条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        color = nodeColor,
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )

            // 右侧内容
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 0.dp, end = 4.dp)
            ) {
                // 节点头部 - 类型名 + ID + 展开按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExpandChange(!isExpanded) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ComfyUILocalization.getNodeTypeName(node.type),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ID: ${node.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 展开后的内容
                if (isExpanded) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Widgets
                    node.widgets.forEach { widget ->
                        WidgetEditor(
                            widget = widget,
                            onWidgetChange = { updatedWidget ->
                                val updatedWidgets = node.widgets.toMutableList()
                                val idx = updatedWidgets.indexOfFirst { it.label == widget.label }
                                if (idx >= 0) {
                                    updatedWidgets[idx] = updatedWidget
                                    onNodeChange(node.copy(widgets = updatedWidgets))
                                }
                            }
                        )
                    }

                    // Inputs
                    if (node.inputs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        PaddingValues(horizontal = 12.dp)
                        Text(
                            "输入 (${node.inputs.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = nodeColor,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        node.inputs.forEach { input ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = input.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (input.value.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = nodeColor.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            input.value,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = nodeColor
                                        )
                                    }
                                } else {
                                    Text(
                                        "未连接",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    // Outputs
                    if (node.outputs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "输出 (${node.outputs.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = nodeColor,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        node.outputs.forEach { output ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = output.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${output.links.size} 连接",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun WidgetEditor(
    widget: EditableWidget,
    onWidgetChange: (EditableWidget) -> Unit
) {
    val isBoolean = widget.value.equals("true", ignoreCase = true) || widget.value.equals("false", ignoreCase = true)
    val isNumber = widget.value.toDoubleOrNull() != null
    val label = ComfyUILocalization.getWidgetLabel(widget.label)

    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        when {
            isBoolean -> {
                StandardSwitch(
                    checked = widget.value.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { checked ->
                        onWidgetChange(widget.copy(value = checked.toString()))
                    },
                    label = label
                )
            }
            else -> {
                StandardTextField(
                    value = widget.value,
                    onValueChange = { newValue ->
                        onWidgetChange(widget.copy(value = newValue))
                    },
                    label = label,
                    isNumber = isNumber
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isNumber: Boolean
) {
    val isMultiline = label.contains("text", true) || label.contains("prompt", true) ||
            label.contains("正向", true) || label.contains("负向", true) ||
            value.length > 40

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        label = { Text(label) },
        singleLine = !isMultiline,
        maxLines = if (isMultiline) 5 else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (isMultiline) ImeAction.Default else ImeAction.Next
        ),
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedOption.ifEmpty { "请选择 $label" },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                shape = RoundedCornerShape(8.dp)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = !expanded }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StandardSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 底部输出面板 - 按原版设计：状态 + 缩略图 + Queue Prompt 按钮
 */
@Composable
fun WorkflowOutputPanel(
    serverUrl: String,
    isGenerating: Boolean,
    progress: Float,
    onGenerate: () -> Unit
) {
    val progressPercent = (progress * 100).toInt()
    val statusText = when {
        isGenerating -> "生成中 $progressPercent%"
        else -> "就绪"
    }
    val statusSubText = when {
        isGenerating -> "正在处理..."
        else -> "查看下方设置"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // 状态行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        statusText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            isGenerating -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        statusSubText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 进度条
            if (isGenerating && progress > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Queue Prompt 全宽按钮
            Button(
                onClick = onGenerate,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成中...", color = Color.White)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Queue Prompt",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
