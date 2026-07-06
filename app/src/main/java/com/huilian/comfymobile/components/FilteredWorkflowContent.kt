package com.huilian.comfymobile.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huilian.comfymobile.data.models.EditableNode
import com.huilian.comfymobile.data.models.FilterPreferences

@Composable
fun FilteredWorkflowContent(
    nodes: List<EditableNode>,
    preferences: FilterPreferences,
    onWidgetValueChange: (String, String, String) -> Unit
) {
    val filtered = if (preferences.selectedNodeTypes.isEmpty()) {
        nodes
    } else {
        nodes.filter { it.type in preferences.selectedNodeTypes }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered) { node ->
            EditableNodeCard(
                editableNode = node,
                onWidgetValueChange = { widgetLabel, value ->
                    onWidgetValueChange(node.id, widgetLabel, value)
                }
            )
        }
    }
}
