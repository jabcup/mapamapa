package com.desarrollodeaplicacionesmoviles.mapamapa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "mapa") {
                composable("mapa") {
                    MapaScreen(
                        onVerPuntos = { navController.navigate("puntos") },
                        onVerBarrios = { navController.navigate("barrios") },
                        onVerFavoritos = { navController.navigate("favoritos") }
                    )
                }
                composable("puntos") {
                    PuntosScreen(onBack = { navController.popBackStack() })
                }
                composable("favoritos") {
                    FavoritosScreen(onBack = { navController.popBackStack() })
                }
                composable("barrios") {
                    // BarriosScreen después
                }
            }
        }
    }
}

@Composable
fun MapaScreen(
    onVerPuntos: () -> Unit,
    onVerBarrios: () -> Unit,
    onVerFavoritos: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            baseStyle = BaseStyle.Uri("https://api.maptiler.com/maps/streets-v2-dark/style.json?key=jphqlsAuqLiPNNpZcewy"),
            cameraState = rememberCameraState(
                firstPosition = CameraPosition(
                    target = Position(-68.1193, -16.5000),
                    zoom = 13.0
                )
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(modifier = Modifier.offset(x = 8.dp, y = 18.dp)) {
                RadialMenuButton(
                    items = listOf(
                        RadialMenuItem(0, Icons.Default.LocationOn, "Puntos", Color.Cyan),
                        RadialMenuItem(1, Icons.Default.Call, "Barrios", Color.Green),
                        RadialMenuItem(2, Icons.Default.Star, "Favoritos", Color.Yellow)
                    ),
                    onItemSelected = { item ->
                        when (item.id) {
                            0 -> onVerPuntos()
                            1 -> onVerBarrios()
                            2 -> onVerFavoritos()
                        }
                    }
                )
            }
        }
    }
}