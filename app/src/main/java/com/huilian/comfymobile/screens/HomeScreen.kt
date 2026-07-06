package com.huilian.comfymobile.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huilian.comfymobile.BuildConfig
import com.huilian.comfymobile.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigate: (Int) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().readText()
                    viewModel.loadWorkflow(json, it.lastPathSegment ?: "imported")
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        HeaderSection(onInfoClick = onNavigateToSettings)
        Spacer(modifier = Modifier.height(16.dp))

        // Server Status Card (expandable)
        ServerStatusExpandableCard(viewModel = viewModel)
        Spacer(modifier = Modifier.height(24.dp))

        // Quick actions
        Text("快捷操作", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickActionCard(
                icon = Icons.Default.Build,
                label = "工作流",
                onClick = { onNavigate(1) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            QuickActionCard(
                icon = Icons.Default.Image,
                label = "图库",
                onClick = { onNavigate(2) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            QuickActionCard(
                icon = Icons.Default.List,
                label = "队列",
                onClick = { onNavigate(3) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickActionCard(
                icon = Icons.Default.FileUpload,
                label = "上传工作流",
                onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            QuickActionCard(
                icon = Icons.Default.Cloud,
                label = "云端",
                onClick = { onNavigate(1) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Saved workflows
        SavedWorkflowsSection(viewModel = viewModel, onNavigate = onNavigate)

        Spacer(modifier = Modifier.height(24.dp))

        // Settings
        Text("设置", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        SettingsSection(
            viewModel = viewModel,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

@Composable
fun HeaderSection(onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Welcome Back",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Text(
                text = "绘联",
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "关于",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ServerStatusExpandableCard(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val isConnected = viewModel.isConnected.value
    val latency = viewModel.serverLatency.value
    val serverUrl = viewModel.serverUrl.value
    val workflowsDirectory = viewModel.workflowsDirectory.value
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Collapsed header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status dot
                    val latencyColor = if (isConnected && latency > 0) {
                        if (latency <= 300) Color(0xFF4CAF50) else Color(0xFFE53935)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                    val dotColor = latencyColor
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Server",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        val statusText = if (isConnected && latency > 0) {
                            "${latency}ms"
                        } else if (isConnected) {
                            "已连接"
                        } else {
                            "未连接"
                        }
                        Text(
                            text = statusText,
                            color = latencyColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Server URL input
                    ServerInputField(
                        label = "Server URL",
                        value = serverUrl,
                        leadingIcon = Icons.Default.Link,
                        onValueChange = { viewModel.serverUrl.value = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Workflows Directory input
                    ServerInputField(
                        label = "Workflows Directory",
                        value = workflowsDirectory,
                        leadingIcon = Icons.Default.Folder,
                        onValueChange = { viewModel.workflowsDirectory.value = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Converter status
                    Text(
                        text = "Converter: not installed",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Scan button
                    OutlinedButton(
                        onClick = { viewModel.scanForComfyUI {} },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan for ComfyUI Servers")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Connect button
                    Button(
                        onClick = {
                            viewModel.updateServerUrl(serverUrl)
                            viewModel.connectToServer()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("连接服务器")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInputField(
    label: String,
    value: String,
    leadingIcon: ImageVector,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SavedWorkflowsSection(
    viewModel: MainViewModel,
    onNavigate: (Int) -> Unit
) {
    val savedWorkflows = viewModel.savedWorkflows
    val serverWorkflows = viewModel.workflowFiles.value

    if (savedWorkflows.isNotEmpty()) {
        Text("已保存的工作流", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        savedWorkflows.forEach { saved ->
            ListItem(
                headlineContent = { Text(saved.name) },
                modifier = Modifier.clickable {
                    viewModel.loadWorkflowFromSaved(saved)
                    onNavigate(1)
                },
                trailingContent = {
                    IconButton(onClick = { viewModel.deleteSavedWorkflow(saved.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    }

    if (serverWorkflows.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("服务器工作流", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        serverWorkflows.forEach { name ->
            ListItem(
                headlineContent = { Text(name) },
                modifier = Modifier.clickable {
                    viewModel.fetchWorkflowFromServer(name)
                    onNavigate(1)
                },
                trailingContent = {
                    Icon(Icons.Default.CloudDownload, contentDescription = "下载")
                }
            )
        }
    }
}

@Composable
fun SettingsSection(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    ListItem(
        headlineContent = { Text("设置") },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable { onNavigateToSettings() }
    )
}
