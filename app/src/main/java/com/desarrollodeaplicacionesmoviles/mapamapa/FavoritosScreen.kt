package com.desarrollodeaplicacionesmoviles.mapamapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
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
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch

// ── Tokens ────────────────────────────────────────────────────────────────────
private val ColorSurface       = Color(0xF5FFFFFF)
private val ColorBorder        = Color(0xFFE0E0E0)
private val ColorTextPrimary   = Color(0xFF0D0D0D)
private val ColorTextSecondary = Color(0xFF757575)
private val ColorTextTertiary  = Color(0xFFB0B0B0)
private val ColorDestructive   = Color(0xFFD32F2F)

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
fun FavoritosScreen(onBack: () -> Unit) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val db        = remember { AppDatabase.getInstance(context) }
    var favoritos by remember { mutableStateOf<List<PuntoFavorito>>(emptyList()) }

    LaunchedEffect(Unit) {
        favoritos = db.favoritosDao().getAll()
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text          = "Favoritos",
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
        if (favoritos.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.dp, ColorBorder, CircleShape)
                            .background(ColorSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Star,
                            contentDescription = null,
                            tint               = ColorTextTertiary,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text       = "Sin favoritos",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = ColorTextPrimary
                    )
                    Text(
                        text       = "Guarda puntos desde el mapa para verlos aquí",
                        fontSize   = 12.sp,
                        color      = ColorTextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.padding(padding),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favoritos) { punto ->
                    FavoritoItem(
                        punto      = punto,
                        onEliminar = {
                            scope.launch {
                                db.favoritosDao().delete(punto)
                                favoritos = db.favoritosDao().getAll()
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
private fun FavoritoItem(
    punto:      PuntoFavorito,
    onEliminar: () -> Unit
) {
    val colorTipo = try { Color(punto.tipo_color.toColorInt()) } catch (e: Exception) { ColorTextTertiary }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cardWithBorder()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // ── Encabezado: chip + eliminar ───────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TypeChip(label = punto.tipo_nombre, dotColor = colorTipo)

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

            Spacer(modifier = Modifier.height(10.dp))

            // ── Nombre ────────────────────────────────────────────────────────
            Text(
                text          = punto.nombre,
                fontSize      = 17.sp,
                fontWeight    = FontWeight.SemiBold,
                color         = ColorTextPrimary,
                letterSpacing = (-0.3).sp
            )

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