package com.desarrollodeaplicacionesmoviles.mapamapa


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
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
fun PuntosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var puntos by remember { mutableStateOf<List<Punto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val db = remember { AppDatabase.getInstance(context) }

    // launch effect ejecuta algo al cargar la pantalla
    LaunchedEffect(Unit) {
        try {
            puntos = RetrofitClient.api.listarPuntos()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Puntos") },
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
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                items(puntos) { punto ->
                    PuntoItem(punto = punto, dao = db.favoritosDao(), scope = scope)
                }
            }
        }
    }
}

@Composable
fun PuntoItem(punto: Punto, dao: FavoritosDao, scope: kotlinx.coroutines.CoroutineScope) {
    var esFavorito by remember { mutableStateOf(false) }

    LaunchedEffect(punto.id) {
        esFavorito = dao.esFavorito(punto.id)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(punto.nombre, style = MaterialTheme.typography.titleMedium)
                Text(punto.descripcion, style = MaterialTheme.typography.bodySmall)
                Text("${punto.latitud}, ${punto.longitud}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                punto.tipo.let {
                    Text("● ${it.nombre}", color = try {
                        Color(android.graphics.Color.parseColor(it.color))
                    } catch (e: Exception) { Color.Gray },
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = {
                scope.launch {
                    if (esFavorito) {
                        dao.delete(PuntoFavorito(punto.id, punto.nombre, punto.descripcion,
                            punto.latitud, punto.longitud, punto.tipo.nombre, punto.tipo.color))
                    } else {
                        dao.insert(PuntoFavorito(punto.id, punto.nombre, punto.descripcion,
                            punto.latitud, punto.longitud, punto.tipo.nombre, punto.tipo.color))
                    }
                    esFavorito = !esFavorito
                }
            }) {
                Icon(
                    imageVector = if (esFavorito) Icons.Filled.Star else Icons.Default.Star,
                    contentDescription = "Favorito",
                    tint = if (esFavorito) Color.Yellow else Color.Gray
                )
            }
        }
    }
}