package com.desarrollodeaplicacionesmoviles.mapamapa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
fun FavoritosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    var favoritos by remember { mutableStateOf<List<PuntoFavorito>>(emptyList()) }

    LaunchedEffect(Unit) {
        favoritos = db.favoritosDao().getAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (favoritos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay favoritos guardados", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(favoritos) { punto ->
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
                                Text("● ${punto.tipo_nombre}",
                                    color = try {
                                        Color(android.graphics.Color.parseColor(punto.tipo_color))
                                    } catch (e: Exception) { Color.Gray },
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    db.favoritosDao().delete(punto)
                                    favoritos = db.favoritosDao().getAll()
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}