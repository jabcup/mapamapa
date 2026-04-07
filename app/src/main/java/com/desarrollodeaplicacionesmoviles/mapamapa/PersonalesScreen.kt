package com.desarrollodeaplicacionesmoviles.mapamapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ── Tokens ────────────────────────────────────────────────────────────────────
private val ColorSurface       = Color(0xF5FFFFFF)
private val ColorSurfaceDark   = Color(0xFF111111)
private val ColorBorder        = Color(0xFFE0E0E0)
private val ColorBorderStrong  = Color(0xFF1A1A1A)
private val ColorTextPrimary   = Color(0xFF0D0D0D)
private val ColorTextSecondary = Color(0xFF757575)
private val ColorTextTertiary  = Color(0xFFB0B0B0)
private val ColorDestructive   = Color(0xFFD32F2F)
private val ColorHome          = Color(0xFF0D47A1)

private fun Modifier.card(): Modifier = this
    .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color(0x14000000))
    .background(ColorSurface, RoundedCornerShape(20.dp))
    .clip(RoundedCornerShape(20.dp))

private fun Modifier.cardWithBorder(): Modifier = this
    .card()
    .border(1.dp, ColorBorder, RoundedCornerShape(20.dp))

@Composable
private fun ThinDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(ColorBorder))
}

@Composable
private fun TypeChip(label: String, dotColor: Color) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
        Text(
            text          = label.uppercase(),
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            color         = ColorTextTertiary,
            letterSpacing = 1.2.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val db      = remember { AppDatabase.getInstance(context) }

    var puntos  by remember { mutableStateOf<List<PuntoPersonal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        puntos  = db.puntosPersonalesDao().getAll()
        loading = false
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text          = "Mis puntos",
                        fontSize      = 17.sp,
                        fontWeight    = FontWeight.SemiBold,
                        color         = ColorTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint               = ColorTextPrimary,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = Color(0xFFF5F5F5),
                    scrolledContainerColor = Color(0xFFF5F5F5)
                )
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color       = ColorTextPrimary,
                    strokeWidth = 2.dp,
                    modifier    = Modifier.size(28.dp)
                )
            }

            puntos.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Icono contenido en un círculo de borde sutil
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.dp, ColorBorder, CircleShape)
                            .background(ColorSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Place,
                            contentDescription = null,
                            tint               = ColorTextTertiary,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text       = "Sin puntos personales",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = ColorTextPrimary
                    )
                    Text(
                        text      = "Toca cualquier lugar del mapa para agregar uno",
                        fontSize  = 12.sp,
                        color     = ColorTextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }

            else -> LazyColumn(
                modifier            = Modifier.padding(padding),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(puntos, key = { it.id }) { punto ->
                    PuntoPersonalItem(
                        punto        = punto,
                        onEliminar   = {
                            scope.launch {
                                db.puntosPersonalesDao().delete(punto)
                                puntos = db.puntosPersonalesDao().getAll()
                            }
                        },
                        onMarcarHome = {
                            scope.launch {
                                db.puntosPersonalesDao().setAsHome(punto.copy(isHome = true))
                                puntos = db.puntosPersonalesDao().getAll()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PuntoPersonalItem(
    punto:        PuntoPersonal,
    onEliminar:   () -> Unit,
    onMarcarHome: () -> Unit
) {
    val chipColor = if (punto.isHome) ColorHome else ColorTextTertiary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cardWithBorder()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // ── Encabezado: chip + acciones ───────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TypeChip(
                    label    = if (punto.isHome) "Hogar" else "Personal",
                    dotColor = chipColor
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Marcar como hogar (solo si no lo es)
                    if (!punto.isHome) {
                        IconButton(
                            onClick  = onMarcarHome,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Home,
                                contentDescription = "Marcar como hogar",
                                tint               = ColorTextTertiary,
                                modifier           = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Eliminar
                    IconButton(
                        onClick  = onEliminar,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint               = ColorDestructive,
                            modifier           = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Nombre ────────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (punto.isHome) {
                    Icon(
                        imageVector        = Icons.Default.Home,
                        contentDescription = null,
                        tint               = ColorHome,
                        modifier           = Modifier.size(15.dp)
                    )
                }
                Text(
                    text          = punto.nombre,
                    fontSize      = 17.sp,
                    fontWeight    = FontWeight.SemiBold,
                    color         = ColorTextPrimary,
                    letterSpacing = (-0.3).sp
                )
            }

            // ── Descripción ───────────────────────────────────────────────────
            if (punto.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text       = punto.descripcion,
                    fontSize   = 13.sp,
                    color      = ColorTextSecondary,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            ThinDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // ── Coordenadas ───────────────────────────────────────────────────
            Text(
                text          = "${punto.latitud}, ${punto.longitud}",
                fontSize      = 11.sp,
                color         = ColorTextTertiary,
                letterSpacing = 0.3.sp,
                fontWeight    = FontWeight.Medium
            )
        }
    }
}