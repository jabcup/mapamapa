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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val haptic = LocalHapticFeedback.current

    // Animación de expansión global
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "expansion"
    )

    val RADIO_DP = 110f  // distancia del centro a cada botón en dp

    Box(
        modifier        = modifier.size(320.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Botones del menú ──────────────────────────────────────────────
        items.forEachIndexed { index, item ->
            // Distribuye los botones en semicírculo hacia arriba (de -30° a -150°)
            // para que no queden tapados por la mano
            val totalAngle = if (items.size == 1) 0f else 160f
            val startAngle = -90f - totalAngle / 2f
            val stepAngle  = if (items.size == 1) 0f else totalAngle / (items.size - 1)
            val angleDeg   = startAngle + stepAngle * index
            val angleRad   = Math.toRadians(angleDeg.toDouble())

            val targetX = (RADIO_DP * cos(angleRad)).toFloat()
            val targetY = (RADIO_DP * sin(angleRad)).toFloat()

            val animX by animateFloatAsState(
                targetValue  = if (isExpanded) targetX else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "x_$index"
            )
            val animY by animateFloatAsState(
                targetValue  = if (isExpanded) targetY else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "y_$index"
            )
            val scale by animateFloatAsState(
                targetValue  = if (isExpanded) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "scale_$index"
            )

            Box(
                modifier        = Modifier
                    .offset { IntOffset(
                        (animX * density).roundToInt(),
                        (animY * density).roundToInt()
                    )}
                    .scale(scale)
                    .alpha(expansionProgress)
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isExpanded = false
                            onItemSelected(item)
                        },
                        modifier       = Modifier.size(48.dp),
                        containerColor = item.color,
                        contentColor   = Color.Black
                    ) {
                        Icon(
                            imageVector        = item.icon,
                            contentDescription = item.label,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                    // Etiqueta debajo del botón
                    if (expansionProgress > 0.5f) {
                        Text(
                            text      = item.label,
                            fontSize  = 10.sp,
                            color     = Color.White,
                            modifier  = Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // ── Botón central ─────────────────────────────────────────────────
        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isExpanded = !isExpanded
            },
            modifier       = Modifier
                .size(56.dp)
                .zIndex(2f),
            containerColor = if (isExpanded) Color(0xFF424242) else Color(0xFF1976D2),
            contentColor   = Color.White
        ) {
            val rotation by animateFloatAsState(
                targetValue  = if (isExpanded) 45f else 0f,
                animationSpec = tween(200),
                label        = "rotation"
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