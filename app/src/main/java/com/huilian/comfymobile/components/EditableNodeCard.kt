package com.huilian.comfymobile.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huilian.comfymobile.data.models.EditableNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableNodeCard(
    editableNode: EditableNode,
    onWidgetValueChange: (String, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = editableNode.type,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = editableNode.type,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            editableNode.widgets.forEach { widget ->
                var text by remember(widget.value) { mutableStateOf(widget.value) }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onWidgetValueChange(widget.label, it)
                    },
                    label = { Text(widget.label) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
