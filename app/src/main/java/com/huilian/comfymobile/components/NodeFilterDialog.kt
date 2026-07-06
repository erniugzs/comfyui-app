package com.huilian.comfymobile.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huilian.comfymobile.data.models.FilterPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeFilterDialog(
    preferences: FilterPreferences,
    onPreferencesChange: (FilterPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    val commonNodeTypes = listOf(
        "CheckpointLoaderSimple",
        "CLIPTextEncode",
        "KSampler",
        "VAEDecode",
        "VAEEncode",
        "EmptyLatentImage",
        "LoadImage",
        "SaveImage",
        "LoraLoader",
        "ControlNetApply"
    )
    var selected by remember { mutableStateOf(preferences.selectedNodeTypes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节点筛选") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(commonNodeTypes) { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = type in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + type else selected - type
                            }
                        )
                        Text(type, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPreferencesChange(preferences.copy(selectedNodeTypes = selected))
                onDismiss()
            }) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
