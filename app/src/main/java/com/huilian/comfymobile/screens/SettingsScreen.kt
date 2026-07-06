package com.huilian.comfymobile.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huilian.comfymobile.BuildConfig
import com.huilian.comfymobile.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var useHttps by remember { mutableStateOf(viewModel.useHttps.value) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("设置") })

        // Connection settings
        SettingsSection(title = "连接设置") {
            ListItem(
                headlineContent = { Text("使用 HTTPS") },
                supportingContent = { Text("启用安全的 HTTPS 连接") },
                trailingContent = {
                    Switch(
                        checked = useHttps,
                        onCheckedChange = {
                            useHttps = it
                            viewModel.toggleHttps()
                        }
                    )
                }
            )
        }

        // Server info
        SettingsSection(title = "服务器信息") {
            ListItem(
                headlineContent = { Text("当前地址") },
                supportingContent = { Text(viewModel.serverUrl.value) }
            )
            ListItem(
                headlineContent = { Text("连接状态") },
                supportingContent = {
                    Text(
                        if (viewModel.isConnected.value) "已连接" else "未连接"
                    )
                }
            )
        }

        // About
        val context = LocalContext.current
        val githubUrl = "https://github.com/erniugzs/comfyui-app"

        SettingsSection(title = "关于") {
            ListItem(
                headlineContent = { Text("应用名称") },
                supportingContent = { Text("绘联") }
            )
            ListItem(
                headlineContent = { Text("版本") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) }
            )
            ListItem(
                headlineContent = { Text("简介") },
                supportingContent = {
                    Text(
                        "绘联是一款基于 Jetpack Compose 构建的 ComfyUI 移动端客户端，支持工作流编辑、图片生成、队列管理和图库浏览。"
                    )
                }
            )
            ListItem(
                headlineContent = { Text("GitHub 项目") },
                supportingContent = { Text(githubUrl) },
                trailingContent = {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "打开")
                },
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column { content() }
        }
    }
}
