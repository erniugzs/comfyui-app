package com.huilian.comfymobile.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterSetupSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var step by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("工作流转换器", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            when (step) {
                0 -> {
                    Text("工作流转换器可以将 ComfyUI 工作流与其他格式互相转换。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. 确保 ComfyUI 服务器已安装转换器插件")
                    Text("2. 选择要转换的工作流")
                    Text("3. 选择目标格式并执行转换")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("下一步")
                    }
                }
                1 -> {
                    Text("选择转换方向:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* convert */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ComfyUI -> A1111")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* convert */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("A1111 -> ComfyUI")
                    }
                }
            }
        }
    }
}
