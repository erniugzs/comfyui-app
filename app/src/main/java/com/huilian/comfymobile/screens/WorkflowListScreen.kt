package com.huilian.comfymobile.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huilian.comfymobile.MainViewModel
import com.huilian.comfymobile.data.SavedWorkflowRepository
import com.huilian.comfymobile.data.models.SavedWorkflow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkflowListScreen(
    viewModel: MainViewModel,
    onWorkflowSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SavedWorkflowRepository(context) }
    var savedWorkflows by remember { mutableStateOf<List<SavedWorkflow>>(emptyList()) }

    LaunchedEffect(Unit) {
        savedWorkflows = repository.loadAll()
        viewModel.fetchWorkflowFiles()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("服务器工作流", "本地保存")

    val serverWorkflows = viewModel.workflowFiles.value
    val starredFiles = remember { mutableStateOf<Set<String>>(emptySet()) }

    val selectedServerFiles = remember { mutableStateListOf<String>() }
    val selectedSavedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedServerFiles.isNotEmpty() || selectedSavedIds.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var renamingWf by remember { mutableStateOf<SavedWorkflow?>(null) }
    var renameValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) {
                            "${selectedServerFiles.size + selectedSavedIds.size} 已选择"
                        } else {
                            "工作流列表"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectedServerFiles.clear()
                            selectedSavedIds.clear()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectionMode) "取消选择" else "返回"
                        )
                    }
                },
                actions = {
                    if (!selectionMode) {
                        IconButton(onClick = {
                            viewModel.fetchWorkflowFiles()
                            scope.launch {
                                savedWorkflows = repository.loadAll()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    } else {
                        IconButton(onClick = {
                            if (selectedTab == 0) {
                                if (selectedServerFiles.size == serverWorkflows.size) {
                                    selectedServerFiles.clear()
                                } else {
                                    selectedServerFiles.clear()
                                    selectedServerFiles.addAll(serverWorkflows)
                                }
                            } else {
                                if (selectedSavedIds.size == savedWorkflows.size) {
                                    selectedSavedIds.clear()
                                } else {
                                    selectedSavedIds.clear()
                                    selectedSavedIds.addAll(savedWorkflows.map { it.id })
                                }
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "全选")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (selectedTab == 1 && selectedSavedIds.isNotEmpty()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        selectedSavedIds.forEach { id ->
                                            repository.delete(id)
                                        }
                                        savedWorkflows = repository.loadAll()
                                        selectedSavedIds.clear()
                                        snackbarHostState.showSnackbar("已删除")
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("删除")
                            }
                        }
                        if (selectedTab == 0 && selectedServerFiles.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    selectedServerFiles.clear()
                                }
                            ) {
                                Text("取消")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            selectedServerFiles.clear()
                            selectedSavedIds.clear()
                        },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (serverWorkflows.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无服务器工作流",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(serverWorkflows, key = { it }) { file ->
                                val isSelected = file in selectedServerFiles
                                WorkflowCard(
                                    file = file,
                                    isStarred = file in starredFiles.value,
                                    isSelected = isSelected,
                                    onStarToggle = {
                                        starredFiles.value = if (file in starredFiles.value) {
                                            starredFiles.value - file
                                        } else {
                                            starredFiles.value + file
                                        }
                                    },
                                    onClick = {
                                        if (selectionMode) {
                                            if (isSelected) selectedServerFiles.remove(file)
                                            else selectedServerFiles.add(file)
                                        } else {
                                            viewModel.fetchWorkflowFromServer(file)
                                            onWorkflowSelected(file)
                                        }
                                    },
                                    onLongClick = {
                                        if (isSelected) selectedServerFiles.remove(file)
                                        else selectedServerFiles.add(file)
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    if (savedWorkflows.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无本地保存",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedWorkflows, key = { it.id }) { wf ->
                                val isSelected = wf.id in selectedSavedIds
                                SavedWorkflowCard(
                                    wf = wf,
                                    isSelected = isSelected,
                                    onOpen = {
                                        if (selectionMode) {
                                            if (isSelected) selectedSavedIds.remove(wf.id)
                                            else selectedSavedIds.add(wf.id)
                                        } else {
                                            viewModel.loadWorkflowFromSaved(wf)
                                            onWorkflowSelected(wf.id)
                                        }
                                    },
                                    onRename = {
                                        renamingWf = wf
                                        renameValue = wf.name
                                    },
                                    onDelete = {
                                        scope.launch {
                                            repository.delete(wf.id)
                                            savedWorkflows = repository.loadAll()
                                            snackbarHostState.showSnackbar("已删除")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (renamingWf != null) {
        AlertDialog(
            onDismissRequest = { renamingWf = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("新名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            renamingWf?.let { wf ->
                                repository.rename(wf.id, renameValue)
                                savedWorkflows = repository.loadAll()
                                snackbarHostState.showSnackbar("已重命名")
                            }
                            renamingWf = null
                        }
                    },
                    enabled = renameValue.isNotBlank()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingWf = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkflowCard(
    file: String,
    isStarred: Boolean,
    isSelected: Boolean,
    onStarToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        } else {
            CardDefaults.elevatedCardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onStarToggle) {
                Icon(
                    if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏",
                    tint = if (isStarred) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedWorkflowCard(
    wf: SavedWorkflow,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateStr = remember(wf.updatedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(wf.updatedAt))
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onOpen
            ),
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        } else {
            CardDefaults.elevatedCardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wf.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "更新于 $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("打开") },
                        onClick = { onOpen(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { onRename(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}
