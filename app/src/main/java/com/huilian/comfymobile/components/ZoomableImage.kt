package com.huilian.comfymobile.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun ZoomableImage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    if (newScale > 1f) {
                        // When zoomed in, adjust offset based on centroid and pan
                        val scaleChange = newScale / scale
                        offsetX = (offsetX + pan.x + (centroid.x - offsetX) * (scaleChange - 1f) / scaleChange)
                        offsetY = (offsetY + pan.y + (centroid.y - offsetY) * (scaleChange - 1f) / scaleChange)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            // Reset zoom
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            // Zoom in 2x centered on tap point
                            scale = 2f
                            offsetX = tapOffset.x
                            offsetY = tapOffset.y
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = if (scale > 1f) {
                        // Clamp panning to image bounds
                        val maxTranslate = (size.width * (scale - 1f)) / 2f
                        offsetX.coerceIn(-maxTranslate, maxTranslate)
                    } else 0f
                    translationY = if (scale > 1f) {
                        val maxTranslate = (size.height * (scale - 1f)) / 2f
                        offsetY.coerceIn(-maxTranslate, maxTranslate)
                    } else 0f
                }
        ) {
            content()
        }
    }
}