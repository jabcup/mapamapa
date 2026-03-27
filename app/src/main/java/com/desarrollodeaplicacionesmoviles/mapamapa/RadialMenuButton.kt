package com.desarrollodeaplicacionesmoviles.mapamapa


import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.sp

data class RadialMenuItem(
    val id: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val color: Color
)

@Composable
fun RadialMenuButton(
    items: List<RadialMenuItem>,
    onItemSelected: (RadialMenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val radius by animateFloatAsState(
        targetValue = if (isExpanded) 115f else 0f,
        label = "radius"
    )

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(300.dp)
            .pointerInput(items, isExpanded) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        if (change.pressed) {
                            if (!isExpanded) {
                                isExpanded = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }

                            val center = this@pointerInput.size.width / 2f
                            val dx = change.position.x - center
                            val dy = change.position.y - center
                            val angleRad = atan2(dy, dx)
                            var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                            if (angleDeg < 0) angleDeg += 360f

                            val sliceSize = 360f / items.size
                            val adjustedAngle = (angleDeg + (sliceSize / 2)) % 360f
                            val index = (adjustedAngle / sliceSize).toInt() % items.size

                            if (selectedIndex != index) {
                                selectedIndex = index
                                if (index in items.indices) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }

                        } else {
                            if (isExpanded) {
                                selectedIndex?.let { index ->
                                    if (index in items.indices) {
                                        onItemSelected(items[index])
                                    }
                                }
                                isExpanded = false
                                selectedIndex = null
                            }
                        }
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {

        if (radius > 10f) {
            items.forEachIndexed { index, item ->
                val sliceSize = (2 * PI) / items.size
                val angle = (sliceSize * index) - (PI / 2)
                val x = (radius * cos(angle)).toFloat()
                val y = (radius * sin(angle)).toFloat()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(x.toInt(), y.toInt()) }
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedIndex == index) item.color else Color.White
                        )
                        .wrapContentSize(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selectedIndex == index) Color.White else Color.Black
                        )
                        if (radius > 150f) {
                            Text(
                                text = item.label,
                                color = if (selectedIndex == index) Color.White else Color.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }


        FloatingActionButton(
            onClick = { },
            modifier = Modifier.zIndex(2f),
            containerColor = if (isExpanded) Color.Gray else Color.Blue
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = "Menu"
            )
        }
    }
}

/*
@Composable
fun EjemploUso() {
    val menuItems = listOf(
        RadialMenuItem(0, Icons.Default.Image, "Foto", Color.Magenta),
        RadialMenuItem(1, Icons.Default.VideoLibrary, "Video", Color.Red),
        RadialMenuItem(2, Icons.Default.TextFields, "Texto", Color.Blue),
        RadialMenuItem(3, Icons.Default.Map, "Mapa", Color.Green)
    )

    Box(Modifier.fillMaxSize()) {
        RadialMenuButton(
            items = menuItems,
            onItemSelected = { item ->
                println("Seleccionado: ${item.label}")
            }
        )
    }
}*/