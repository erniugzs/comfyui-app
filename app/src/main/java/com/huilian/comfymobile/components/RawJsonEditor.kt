package com.huilian.comfymobile.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawJsonEditor(
    initialJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf(initialJson) }
    var isValid by remember { mutableStateOf(true) }

    fun validateJson(text: String): Boolean {
        return try {
            JsonParser.parseString(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun formatJson(text: String): String {
        return try {
            val element = JsonParser.parseString(text)
            GsonBuilder().setPrettyPrinting().create().toJson(element)
        } catch (e: Exception) {
            text
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("原始 JSON") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        jsonText = formatJson(jsonText)
                        isValid = validateJson(jsonText)
                    }) {
                        Icon(Icons.Default.FormatAlignLeft, contentDescription = "格式化")
                    }
                    TextButton(
                        onClick = {
                            if (validateJson(jsonText)) {
                                onSave(jsonText)
                            }
                        },
                        enabled = isValid
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!isValid) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "JSON 格式无效，请检查语法",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            OutlinedTextField(
                value = jsonText,
                onValueChange = {
                    jsonText = it
                    isValid = validateJson(it)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp
                )
            )
        }
    }
}
