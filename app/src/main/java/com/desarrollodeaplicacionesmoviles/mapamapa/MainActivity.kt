package com.desarrollodeaplicacionesmoviles.mapamapa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.android.geometry.LatLng
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlin.Double.Companion.NaN

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "mapa") {
                composable("mapa") {
                    Column(modifier = Modifier.padding(bottom = 0.dp, top = 0.dp)) {
                        MapaScreen(
                            onVerPuntos = { navController.navigate("puntos") },
                            onVerBarrios = { navController.navigate("barrios") },
                            onVerFavoritos = { navController.navigate("favoritos") }
                        )
                    }
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

    var puntos by remember { mutableStateOf<List<Punto>>(emptyList()) }
    var puntoSeleccionado by remember { mutableStateOf<Punto?>(null) }

    LaunchedEffect(Unit) {
        try {
            puntos = RetrofitClient.api.listarPuntos()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val locationProvider = rememberDefaultLocationProvider()
    val locationState = rememberUserLocationState(locationProvider)
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                locationState.location?.position?.longitude ?: -68.1193,
                locationState.location?.position?.latitude ?: -16.5000
            ),
            zoom = 15.0
        )
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

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
        //
        // Toast.makeText(context, "ubicacion siendo ${locationState.location?.position?.longitude ?: 0.0} y ${locationState.location?.position?.longitude ?: 0.0}", Toast.LENGTH_SHORT).show()

        MaplibreMap(
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState,
            onMapClick = { _, _ ->
                // Toque en área vacía: cierra la card
                puntoSeleccionado = null
                ClickResult.Pass
            }
        ) {
            // Only show LocationPuck if location data is valid
          //  val cameraState = rememberCameraState()

            locationState.location?.let { location ->

                val coords = Position(
                    latitude = location.position.latitude,
                    longitude = location.position.longitude
                )

                LaunchedEffect(coords) {
                    cameraState.animateTo(
                        CameraPosition(
                            target = coords,
                            zoom = 15.0
                        )
                    )
                }

                LocationPuck(
                    idPrefix = "user-location",
                    locationState = locationState,
                    cameraState = cameraState
                )
            }

            CircleLayerPlacitas(
                puntos = puntos,
                onPuntoClick = { punto ->
                    puntoSeleccionado = punto
                }
            )
        }

        // Card flotante del punto seleccionado
        puntoSeleccionado?.let { punto ->
            PuntoInfoCard(
                punto = punto,
                onDismiss = { puntoSeleccionado = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Botón radial
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

@Composable
private fun PuntoInfoCard(
    punto: Punto,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    //obtengo la ubicación desde acá? otra ve?
    val locationProvider = rememberDefaultLocationProvider()
    val locationState = rememberUserLocationState(locationProvider)

    // Estado para guardar la polyline obtenida
    var polyline by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun getPolyline(lat: String, long: String):String?{
        if (isLoading) return "aaa"


            isLoading = true
            errorMsg = null
            try {
                val polyline = RetrofitClient.api.obtenerPolylineSimple(
                    locationState.location?.position?.latitude.toString(),
                    locationState.location?.position?.longitude.toString(),
                    lat,
                    long)
                println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                println("polyline es: ${polyline}")
                return polyline
            }
            catch (e: Exception){
                errorMsg = e.message ?: "Error desconocido"
                //return errorMsg
            }
            finally {
                isLoading = false
            }


        return polyline
    }


    val colorTipo = try {
        Color(punto.tipo.color.toColorInt())
    } catch (e: Exception) {
        Color.Gray
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(colorTipo, CircleShape)
                    )
                    Text(
                        text = punto.tipo.nombre,
                        fontSize = 12.sp,
                        color = colorTipo,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current

                    Button(onClick = {

                        //Toast.makeText(context, "calcule la ruta hastas ${punto.nombre}", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val ruta = getPolyline(punto.longitud, punto.latitud)
                            Toast.makeText(context, "polyline ser: ${ruta}", Toast.LENGTH_SHORT).show()
                        }

                    }) { Text("Ruta") }
                    Button(onClick = {
                        Toast.makeText(context, "agregao a favoritos el ${punto.nombre}", Toast.LENGTH_SHORT).show() }) { Text("Favoritar") }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = punto.nombre,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = punto.descripcion,
                fontSize = 14.sp,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
private fun CircleLayerPlacitas(
    puntos: List<Punto>,
    onPuntoClick: (Punto) -> Unit
) {
    if (puntos.isEmpty()) return

    val dotSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                puntos.map { punto ->
                    Feature(
                        geometry = Point(
                            Position(
                                punto.latitud.toDouble(),
                                punto.longitud.toDouble()
                            )
                        ),
                        properties = JsonObject(
                            mapOf(
                                "color" to JsonPrimitive(punto.tipo.color),
                                "id"    to JsonPrimitive(punto.id)
                            )
                        )
                    )
                }
            )
        )
    )

    CircleLayer(
        id = "circle-layer-placitas",
        source = dotSource,
        // Fixed: use convertToColor() instead of asColor()
        color = feature["color"].convertToColor(),
        radius = const(10.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        minZoom = 10f,
        onClick = { features ->
            val clickedId = features.firstOrNull()
                ?.properties
                ?.get("id")
                ?.toString()
                ?.toIntOrNull()

            if (clickedId != null) {
                puntos.find { it.id == clickedId }?.let { onPuntoClick(it) }
            }
            ClickResult.Consume
        }
    )
}