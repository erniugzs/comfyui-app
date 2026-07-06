package com.huilian.comfymobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.huilian.comfymobile.MainViewModel
import com.huilian.comfymobile.components.SwipeableImageViewer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(viewModel: MainViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images = viewModel.galleryImages
    val isLoading by viewModel.isLoading

    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshTimestamp = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.fetchGalleryImages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("图库", fontWeight = FontWeight.Bold)
                        Text("${images.size} items", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.fetchGalleryImages()
                            refreshTimestamp.longValue = System.currentTimeMillis()
                            isRefreshing = false
                        }
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.fetchGalleryImages()
                    refreshTimestamp.longValue = System.currentTimeMillis()
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(paddingValues),
            state = pullRefreshState
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (images.isEmpty() && !isLoading) {
                        EmptyGalleryState(onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                viewModel.fetchGalleryImages()
                                refreshTimestamp.longValue = System.currentTimeMillis()
                                isRefreshing = false
                            }
                        })
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(images.size, key = { images[it] }) { index ->
                                val filename = images[index]
                                val imageUrl = remember(filename, refreshTimestamp.longValue) {
                                    viewModel.getImageUrl(filename) + "&t=" + refreshTimestamp.longValue
                                }
                                GalleryThumbnail(
                                    imageUrl = imageUrl,
                                    onClick = {
                                        selectedImageIndex = index
                                        isFullscreen = true
                                    }
                                )
                            }
                        }
                    }

                    if (isLoading && !isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    // 全屏查看时拦截返回键/滑动返回
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        selectedImageIndex = null
    }

    LaunchedEffect(isFullscreen) {
        viewModel.inFullscreenViewer.value = isFullscreen
    }

    if (isFullscreen) {
        val index = selectedImageIndex ?: return
        val imageUrls = remember(images, refreshTimestamp.longValue) {
            images.map { viewModel.getImageUrl(it) + "&t=" + refreshTimestamp.longValue }
        }
        SwipeableImageViewer(
            images = imageUrls,
            imageNames = images.toList(),
            initialIndex = index,
            onDismiss = {
                isFullscreen = false
                selectedImageIndex = null
            },
            onPageChanged = { page ->
                selectedImageIndex = page
            },
            onSave = { filename ->
                viewModel.saveImageToGallery(context, filename)
            },
            onShare = { filename ->
                viewModel.shareImage(context, filename)
            }
        )
    }
}

@Composable
fun GalleryThumbnail(imageUrl: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                }
            }
        )
    }
}

@Composable
fun EmptyGalleryState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "暂无图片",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRefresh) {
            Text("刷新")
        }
    }
}
