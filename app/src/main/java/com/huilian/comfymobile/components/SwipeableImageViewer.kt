package com.huilian.comfymobile.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * 全屏图片查看器
 * - HorizontalPager 左右滑动切换图片（未放大时）
 * - 双击切换 1x ↔ 2.5x 缩放
 * - 双指捏合缩放 1x ~ 5x
 * - 放大后单指平移查看细节，边缘回弹
 * - 放大时禁用 HorizontalPager，避免与返回手势冲突
 * - 单击切换顶部/底部 UI 显示
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipeableImageViewer(
    images: List<String>,
    imageNames: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onPageChanged: ((Int) -> Unit)? = null,
    onSave: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { images.size }, initialPage = initialIndex)

    // 是否处于放大状态
    var isZoomed by remember { mutableStateOf(false) }
    var showUi by remember { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged?.invoke(pagerState.currentPage)
        isZoomed = false
        showUi = true
    }

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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 放大时禁用 pager 左右滑动，避免与平移和全局返回手势冲突
            userScrollEnabled = !isZoomed
        ) { page ->
            val imageUrl = images[page]
            ZoomableImage(
                imageUrl = imageUrl,
                contentDescription = imageNames.getOrNull(page),
                isZoomed = isZoomed,
                onZoomChange = { zoomed ->
                    isZoomed = zoomed
                },
                onTap = {
                    showUi = !showUi
                }
            )
        }

        // 顶部/底部控制栏
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val currentImageName = imageNames.getOrNull(pagerState.currentPage)

                ViewerTopBar(
                    title = "图片 ${pagerState.currentPage + 1} / ${images.size}",
                    onDismiss = onDismiss,
                    onSave = onSave?.let { cb ->
                        { currentImageName?.let { name -> cb(name) } }
                    },
                    onShare = onShare?.let { cb ->
                        { currentImageName?.let { name -> cb(name) } }
                    }
                )

                // 底部文件名 + 导航
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    val filename = currentImageName?.substringAfterLast("/") ?: ""
                    if (filename.isNotEmpty()) {
                        Text(
                            text = filename,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            enabled = pagerState.currentPage > 0
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上一张",
                                tint = if (pagerState.currentPage > 0) Color.White else Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            enabled = pagerState.currentPage < images.size - 1
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "下一张",
                                tint = if (pagerState.currentPage < images.size - 1) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBar(
    title: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)?,
    onShare: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        },
        actions = {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).padding(4.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                onShare?.let {
                    IconButton(onClick = {
                        scope.launch {
                            isSaving = true
                            try { it() } finally { isSaving = false }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color.White)
                    }
                }
                onSave?.let {
                    IconButton(onClick = {
                        scope.launch {
                            isSaving = true
                            try { it() } finally { isSaving = false }
                        }
                    }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "保存", tint = Color.White)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        )
    )
}

/**
 * 可缩放、可平移的图片组件
 * - 双击：1x ↔ 2.5x 切换
 * - 双指缩放：1x ~ 5x
 * - 放大后单指拖动平移，边缘回弹（spring 动画）
 * - 单击：切换 UI 显示
 */
@Composable
private fun ZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    isZoomed: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 缩放比例，范围 1f ~ 5f
    val scale = remember { Animatable(1f) }
    // 平移偏移
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 外部重置（切换页面时）
    LaunchedEffect(isZoomed) {
        if (!isZoomed) {
            scale.animateTo(1f)
            offsetX.animateTo(0f)
            offsetY.animateTo(0f)
            onZoomChange(false)
        }
    }

    // 计算平移边界
    fun getMaxOffset(): Offset {
        val sx = scale.value
        val maxX = containerSize.width * (sx - 1f) / 2f
        val maxY = containerSize.height * (sx - 1f) / 2f
        return Offset(maxX, maxY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            // 双击 + 单击
            .pointerInput(scale) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scale.value > 1.1f) {
                                // 已放大 → 回到 1x
                                scale.animateTo(1f)
                                offsetX.animateTo(0f)
                                offsetY.animateTo(0f)
                                onZoomChange(false)
                            } else {
                                // 未放大 → 2.5x，聚焦点击位置
                                scale.animateTo(2.5f)
                                // 以点击位置为中心计算偏移
                                val max = Offset(
                                    containerSize.width * 1.5f / 2f,
                                    containerSize.height * 1.5f / 2f
                                )
                                val focusX = (tapOffset.x - containerSize.width / 2f) * 2.5f
                                val focusY = (tapOffset.y - containerSize.height / 2f) * 2.5f
                                offsetX.animateTo(focusX.coerceIn(-max.x, max.x))
                                offsetY.animateTo(focusY.coerceIn(-max.y, max.y))
                                onZoomChange(true)
                            }
                        }
                    },
                    onTap = { onTap() }
                )
            }
            // 双指缩放 + 单指平移
            .pointerInput(containerSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scope.launch {
                        val newScale = (scale.value * zoom).coerceIn(1f, 5f)

                        if (newScale > 1.05f) {
                            onZoomChange(true)
                        } else if (scale.value > 1.05f && newScale <= 1.05f) {
                            // 缩小到接近 1x，回弹到 1x
                            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            onZoomChange(false)
                            return@launch
                        }

                        scale.snapTo(newScale)

                        // 平移
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
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                },
            contentScale = ContentScale.Fit,
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.Red)
                }
            }
        )
    }
}
