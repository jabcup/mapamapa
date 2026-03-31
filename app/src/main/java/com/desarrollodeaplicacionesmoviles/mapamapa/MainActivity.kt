package com.desarrollodeaplicacionesmoviles.mapamapa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle

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
                composable("barrios") {}
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
    val context = LocalContext.current

    // Proper location setup
    val locationProvider = rememberDefaultLocationProvider()
    val locationState = rememberUserLocationState(locationProvider)

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = org.maplibre.spatialk.geojson.Position(-68.1193, -16.5000),
            zoom = 13.0
        )
    )

    // Permission launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Location provider will automatically start when permission is granted
    }

    // Check and request permission
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState
        ) {
            // Use the built-in LocationPuck instead of manual CircleLayer
            LocationPuck(
                idPrefix = "user-location",
                locationState = locationState,
                cameraState = cameraState
            )
        }

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
