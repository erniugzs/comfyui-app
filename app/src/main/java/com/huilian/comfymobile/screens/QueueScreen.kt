package com.huilian.comfymobile.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.huilian.comfymobile.MainViewModel
import com.huilian.comfymobile.data.models.QueueItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {},
    onViewWorkflow: () -> Unit = {}
) {
    val queueItems by viewModel.queueItems
    val isRefreshing by viewModel.isRefreshing
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fullscreenImageUrl = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshHistory()
    }

    LaunchedEffect(viewModel.errorMessage.value) {
        val msg = viewModel.errorMessage.value
        if (msg.isNotBlank()) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("队列") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            viewModel.refreshHistory()
                        }
                    }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (queueItems.isEmpty()) {
                EmptyQueueState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(queueItems, key = { it.id }) { item ->
                        SwipeableQueueItem(
                            item = item,
                            viewModel = viewModel,
                            onViewImage = { url -> fullscreenImageUrl.value = url }
                        )
                    }
                }
            }
        }
    }

    // 全屏图片查看时拦截返回键/滑动返回
    BackHandler(enabled = fullscreenImageUrl.value != null) {
        fullscreenImageUrl.value = null
    }

    LaunchedEffect(fullscreenImageUrl.value) {
        viewModel.inFullscreenViewer.value = fullscreenImageUrl.value != null
    }

    fullscreenImageUrl.value?.let { url ->
        ZoomableQueueImageViewer(
            imageUrl = url,
            onDismiss = { fullscreenImageUrl.value = null }
        )
    }
}

@Composable
fun SwipeableQueueItem(
    item: QueueItem,
    viewModel: MainViewModel,
    onViewImage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 120.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var isRevealed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
    ) {
        // 红色删除按钮背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .background(Color(0xFFE53935))
                .clickable {
                    scope.launch {
                        viewModel.deleteQueueTask(item)
                    }
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.padding(end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "删除",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 前景内容（拖动 + 点击都处理在这里）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .pointerInput(item.id) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = offsetX.value },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value > maxOffsetPx * 0.3f) {
                                    offsetX.animateTo(maxOffsetPx, tween(200))
                                    isRevealed = true
                                } else {
                                    offsetX.animateTo(0f, tween(200))
                                    isRevealed = false
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, tween(200))
                                isRevealed = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            scope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxOffsetPx)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable(enabled = !isRevealed) {
                    if (item.imageUrl != null) {
                        onViewImage(item.imageUrl)
                    }
                }
        ) {
            QueueListItemNoClick(
                item = item,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun EmptyQueueState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无历史记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "运行工作流以查看生成结果",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun QueueListItemNoClick(
    item: QueueItem,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val progress = viewModel.progressMap.value[item.id] ?: 0f
    val progressPercent = (progress * 100).toInt()
    val isRunning = item.status == "running"
    val isCompleted = item.status == "completed"
    val isError = item.status == "error"
    val statusColor = when {
        isCompleted -> Color(0xFF4CAF50)
        isRunning -> MaterialTheme.colorScheme.primary
        isError -> MaterialTheme.colorScheme.error
        else -> Color.Gray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(35.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.imageUrl)
                            .memoryCacheKey(item.imageUrl)
                            .diskCacheKey(item.imageUrl)
                            .crossfade(true)
                            .size(coil.size.Size.ORIGINAL)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (isRunning) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(35.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else if (isError) {
                    Icon(
                        imageVector = Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.imageName.ifEmpty { item.id.take(8) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (isRunning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${progressPercent}%",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    } else if (isCompleted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "完成",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    } else if (isError) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "失败",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateText = remember(item.timestamp) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                    }
                    Text(
                        text = dateText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    if (item.imageSize.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.imageSize,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (isRunning && progress > 0f) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
                strokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap
            )
        }
    }
}

@Composable
fun ZoomableQueueImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showUi by remember { mutableStateOf(true) }

    LaunchedEffect(showUi) {
        if (showUi) {
            kotlinx.coroutines.delay(3000)
            showUi = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ZoomableQueueImage(
            imageUrl = imageUrl,
            onTap = { showUi = !showUi }
        )

        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ZoomableQueueImage(
    imageUrl: String,
    onTap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(scale) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scale.value > 1.1f) {
                                scale.animateTo(1f)
                                offsetX.animateTo(0f)
                                offsetY.animateTo(0f)
                            } else {
                                scale.animateTo(2.5f)
                                val max = Offset(
                                    containerSize.width * 1.5f / 2f,
                                    containerSize.height * 1.5f / 2f
                                )
                                val focusX = (tapOffset.x - containerSize.width / 2f) * 2.5f
                                val focusY = (tapOffset.y - containerSize.height / 2f) * 2.5f
                                offsetX.animateTo(focusX.coerceIn(-max.x, max.x))
                                offsetY.animateTo(focusY.coerceIn(-max.y, max.y))
                            }
                        }
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(containerSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scope.launch {
                        val newScale = (scale.value * zoom).coerceIn(1f, 5f)

                        if (scale.value > 1.05f && newScale <= 1.05f) {
                            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            return@launch
                        }

                        scale.snapTo(newScale)

                        val max = Offset(
                            containerSize.width * (newScale - 1f) / 2f,
                            containerSize.height * (newScale - 1f) / 2f
                        )
                        offsetX.snapTo((offsetX.value + pan.x).coerceIn(-max.x, max.x))
                        offsetY.snapTo((offsetY.value + pan.y).coerceIn(-max.y, max.y))
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = "全屏图片",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                },
            contentScale = ContentScale.Fit
        )
    }
}
