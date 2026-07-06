package com.huilian.comfymobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.huilian.comfymobile.screens.*
import com.huilian.comfymobile.ui.theme.ComfyUIMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 配置 Coil 图片缓存：内存 25% 可用 + 磁盘 100MB
        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(100L * 1024 * 1024).build() }
                .crossfade(true)
                .build()
        }

        setContent {
            ComfyUIMobileTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, onFinish: () -> Unit) {
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Workflows,
        BottomNavItem.Gallery,
        BottomNavItem.Queue,
        BottomNavItem.Settings
    )

    var showWorkflowEditor by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val onBackPressedDispatcher = (context as? ComponentActivity)?.onBackPressedDispatcher
    val density = LocalDensity.current
    val edgeThresholdPx = remember { with(density) { 40.dp.toPx() } }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 全局滑动返回手势：屏幕边缘左滑/右滑触发返回
    // 当图库全屏查看器打开时，边缘手势由查看器内部处理，全局手势禁用
    val edgeSwipeEnabled = !viewModel.inFullscreenViewer.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(edgeThresholdPx, edgeSwipeEnabled) {
                if (!edgeSwipeEnabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val startX = down.position.x
                        val screenWidth = size.width.toFloat()

                        // 只在屏幕边缘开始的手势才跟踪（避免和 HorizontalPager 冲突）
                        if (startX > edgeThresholdPx && startX < screenWidth - edgeThresholdPx) {
                            continue
                        }

                        var totalDrag = 0f
                        var active = true

                        while (active) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                active = false
                                break
                            }

                            totalDrag = change.position.x - startX

                            if (!change.pressed) {
                                if (kotlin.math.abs(totalDrag) > 120f) {
                                    onBackPressedDispatcher?.onBackPressed()
                                }
                                active = false
                            }
                        }
                    }
                }
            }
    ) {
        // 全局 BackHandler：处理所有未被子页面拦截的返回
        BackHandler {
            if (showWorkflowEditor) {
                showWorkflowEditor = false
            } else if (selectedItem != 0) {
                selectedItem = 0
            } else {
                onFinish()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!showWorkflowEditor) {
                    NavigationBar {
                        items.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        if (selectedItem == index) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) },
                                selected = selectedItem == index,
                                onClick = { selectedItem = index }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when {
                    showWorkflowEditor -> WorkflowScreen(
                        viewModel = viewModel,
                        onGenerate = { json ->
                            viewModel.generateImage()
                        },
                        onBack = { showWorkflowEditor = false }
                    )
                    selectedItem == 0 -> HomeScreen(
                        viewModel = viewModel,
                        onNavigate = { index -> selectedItem = index },
                        onNavigateToSettings = { selectedItem = 4 }
                    )
                    selectedItem == 1 -> WorkflowListScreen(
                        viewModel = viewModel,
                        onWorkflowSelected = { showWorkflowEditor = true },
                        onBack = { selectedItem = 0 }
                    )
                    selectedItem == 2 -> GalleryScreen(viewModel = viewModel)
                    selectedItem == 3 -> QueueScreen(viewModel = viewModel)
                    selectedItem == 4 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
