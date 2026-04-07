package com.desarrollodeaplicacionesmoviles.mapamapa


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    var puntos by remember { mutableStateOf<List<PuntoPersonal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        puntos = db.puntosPersonalesDao().getAll()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis puntos personales") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            puntos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector        = Icons.Default.Home,
                        contentDescription = null,
                        modifier           = Modifier.size(48.dp),
                        tint               = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No tienes puntos personales guardados", color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Toca el mapa para agregar uno", color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                items(puntos, key = { it.id }) { punto ->
                    PuntoPersonalItem(
                        punto     = punto,
                        onEliminar = {
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

@Composable
fun PuntoPersonalItem(
    punto:        PuntoPersonal,
    onEliminar:   () -> Unit,
    onMarcarHome: () -> Unit
) {
    val colorHome     = Color(0xFFE65100)
    val colorPersonal = Color(0xFF424242)
    val colorIndicador = if (punto.isHome) colorHome else colorPersonal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de color a la izquierda
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(colorIndicador, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (punto.isHome) {
                        Icon(
                            imageVector        = Icons.Default.Home,
                            contentDescription = "Hogar",
                            tint               = colorHome,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                    Text(punto.nombre, style = MaterialTheme.typography.titleMedium)
                }

                if (punto.descripcion.isNotBlank()) {
                    Text(
                        text  = punto.descripcion,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text  = if (punto.isHome) "Mi hogar" else "Punto personal",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorIndicador
                )
            }

            // Botón marcar como hogar (solo si no lo es ya)
            if (!punto.isHome) {
                IconButton(onClick = onMarcarHome) {
                    Icon(
                        imageVector        = Icons.Default.Home,
                        contentDescription = "Marcar como hogar",
                        tint               = Color.Gray
                    )
                }
            }

            // Botón eliminar
            IconButton(onClick = onEliminar) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint               = Color(0xFFB71C1C)
                )
            }
        }
    }
}