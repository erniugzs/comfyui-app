package com.huilian.comfymobile.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudWorkflowBrowserSheet(
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var workflows by remember { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("云端工作流", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { /* reload */ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (workflows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无云端工作流")
                }
            } else {
                LazyColumn {
                    items(workflows.size) { idx ->
                        ListItem(
                            headlineContent = { Text(workflows[idx]) },
                            trailingContent = {
                                IconButton(onClick = { onDownload(workflows[idx]) }) {
                                    Icon(Icons.Default.Download, contentDescription = "下载")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
