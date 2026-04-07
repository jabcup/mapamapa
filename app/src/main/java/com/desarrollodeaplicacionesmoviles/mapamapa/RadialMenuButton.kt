package com.desarrollodeaplicacionesmoviles.mapamapa

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.sin

data class RadialMenuItem(
    val id:    Int,
    val icon:  androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val color: Color
)

@Composable
fun RadialMenuButton(
    items:          List<RadialMenuItem>,
    onItemSelected: (RadialMenuItem) -> Unit,
    modifier:       Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic  = LocalHapticFeedback.current
    val density = LocalDensity.current

    val expansionProgress by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "expansion"
    )

    // Radio más grande y arco más abierto para que los botones respiren
    val RADIO_DP   = 140f
    val totalAngle = if (items.size == 1) 0f else 100f   // más abierto que 90°
    val startAngle = -90f                                  // arriba
    val stepAngle  = if (items.size == 1) 0f else totalAngle / (items.size - 1)

    Box(
        modifier         = modifier.size(380.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        items.forEachIndexed { index, item ->
            val angleDeg = startAngle - stepAngle * index
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val targetX = (RADIO_DP * cos(angleRad)).toFloat()
            val targetY = (RADIO_DP * sin(angleRad)).toFloat()

            val animX by animateFloatAsState(
                targetValue   = if (isExpanded) targetX else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "x_$index"
            )
            val animY by animateFloatAsState(
                targetValue   = if (isExpanded) targetY else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "y_$index"
            )
            val itemScale by animateFloatAsState(
                targetValue   = if (isExpanded) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "scale_$index"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset {
                        IntOffset(
                            x = with(density) { animX.dp.roundToPx() },
                            y = with(density) { animY.dp.roundToPx() }
                        )
                    }
                    .scale(itemScale)
                    .alpha(expansionProgress)
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (expansionProgress > 0.5f) {
                        Text(
                            text     = item.label,
                            fontSize = 10.sp,
                            color    = Color.White,
                            modifier = Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isExpanded = false
                            onItemSelected(item)
                        },
                        modifier       = Modifier.size(48.dp),
                        containerColor = item.color,
                        contentColor   = Color.White
                    ) {
                        Icon(
                            imageVector        = item.icon,
                            contentDescription = item.label,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isExpanded = !isExpanded
            },
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .size(56.dp)
                .zIndex(2f),
            shape          = CircleShape,
            containerColor = if (isExpanded) Color(0xFF212121) else Color(0xFF111111),
            contentColor   = Color.White
        ) {
            val rotation by animateFloatAsState(
                targetValue   = if (isExpanded) 45f else 0f,
                animationSpec = tween(200),
                label         = "rotation"
            )
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = if (isExpanded) "Cerrar" else "Abrir menú",
                modifier           = Modifier
                    .size(28.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}