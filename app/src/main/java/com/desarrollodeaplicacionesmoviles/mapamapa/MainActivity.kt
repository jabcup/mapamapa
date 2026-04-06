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
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
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
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

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
    val db = remember { AppDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var puntos by remember { mutableStateOf<List<Punto>>(emptyList()) }
    var puntosPersonales by remember { mutableStateOf<List<PuntoPersonal>>(emptyList()) }
    var puntoSeleccionado by remember { mutableStateOf<Punto?>(null) }
    var ruta by remember { mutableStateOf<String?>(null) }
    var puntoPersonalSeleccionado by remember { mutableStateOf<Position?>(null) }

    LaunchedEffect(Unit) {
        try {
            puntos = RetrofitClient.api.listarPuntos()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        try {
            puntosPersonales = db.puntosPersonalesDao().getAll()
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

        MaplibreMap(
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState,
            onMapClick = { position, _ ->
                if (puntoSeleccionado == null) {
                    puntoPersonalSeleccionado = position
                } else {
                    puntoSeleccionado = null
                }
                ClickResult.Pass
            }
        ) {
            locationState.location?.let { location ->
                val coords = Position(
                    latitude = location.position.latitude,
                    longitude = location.position.longitude
                )
                LaunchedEffect(coords) {
                    cameraState.animateTo(
                        CameraPosition(target = coords, zoom = 15.0)
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
                    puntoPersonalSeleccionado = null
                    puntoSeleccionado = punto
                }
            )

            CircleLayerPersonales(
                puntosPersonales = puntosPersonales,
                onPuntoClick = { _ ->
                    // por ahora solo cierra cualquier card abierta
                    puntoSeleccionado = null
                    puntoPersonalSeleccionado = null
                }
            )

            ruta?.let { encodedPolyline ->
                val posiciones = remember(encodedPolyline) {
                    decodificarPolyline(encodedPolyline)
                }
                if (posiciones.size >= 2) {
                    val rutaSource = rememberGeoJsonSource(
                        data = GeoJsonData.Features(
                            FeatureCollection(
                                listOf(
                                    Feature(
                                        geometry = LineString(posiciones),
                                        properties = JsonObject(emptyMap())
                                    )
                                )
                            )
                        )
                    )
                    LineLayer(
                        id = "ruta-layer",
                        source = rutaSource,
                        color = const(Color(0xFF1976D2)),
                        width = const(5.dp)
                    )
                }
            }
        }

        puntoSeleccionado?.let { punto ->
            PuntoInfoCard(
                punto = punto,
                onDismiss = {
                    puntoSeleccionado = null
                    ruta = null
                },
                onRutaCalculada = { polylineString ->
                    ruta = polylineString
                },
                onFavoritar = {
                    coroutineScope.launch {
                        db.favoritosDao().insert(
                            PuntoFavorito(
                                id = punto.id,
                                nombre = punto.nombre,
                                descripcion = punto.descripcion,
                                latitud = punto.latitud,
                                longitud = punto.longitud,
                                tipo_nombre = punto.tipo.nombre,
                                tipo_color = punto.tipo.color
                            )
                        )
                        Toast.makeText(context, "${punto.nombre} agregado a favoritos", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        puntoPersonalSeleccionado?.let { posicion ->
            PuntoPersonalCard(
                posicion = posicion,
                onDismiss = { puntoPersonalSeleccionado = null },
                onGuardar = { nombre, descripcion, isHome ->
                    coroutineScope.launch {
                        if (isHome) {
                            db.puntosPersonalesDao().setAsHome(
                                PuntoPersonal(
                                    nombre = nombre,
                                    descripcion = descripcion,
                                    latitud = posicion.latitude.toString(),
                                    longitud = posicion.longitude.toString(),
                                    isHome = true
                                )
                            )
                        } else {
                            db.puntosPersonalesDao().insert(
                                PuntoPersonal(
                                    nombre = nombre,
                                    descripcion = descripcion,
                                    latitud = posicion.latitude.toString(),
                                    longitud = posicion.longitude.toString()
                                )
                            )
                        }
                        puntosPersonales = db.puntosPersonalesDao().getAll()
                        Toast.makeText(context, "$nombre guardado", Toast.LENGTH_SHORT).show()
                        puntoPersonalSeleccionado = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
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

@Composable
private fun PuntoPersonalCard(
    posicion: Position,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String, descripcion: String, isHome: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var isHome by remember { mutableStateOf(false) }

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
                Text(
                    text = "Nuevo punto personal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isHome,
                    onCheckedChange = { isHome = it }
                )
                Text(text = "Marcar como hogar", fontSize = 14.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (nombre.isNotBlank()) {
                        onGuardar(nombre, descripcion, isHome)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun PuntoInfoCard(
    punto: Punto,
    onDismiss: () -> Unit,
    onRutaCalculada: (String) -> Unit,
    onFavoritar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locationProvider = rememberDefaultLocationProvider()
    val locationState = rememberUserLocationState(locationProvider)

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun getPolyline(lat: String, long: String): String? {
        if (isLoading) return null
        isLoading = true
        errorMsg = null
        try {
            val userLat = locationState.location?.position?.latitude.toString()
            val userLng = locationState.location?.position?.longitude.toString()
            val resultado = RetrofitClient.api.obtenerPolylineSimple(userLat, userLng, lat, long)
            return resultado
        } catch (e: Exception) {
            errorMsg = e.message ?: "Error desconocido"
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isLoading = false
        }
        return null
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
                    Button(onClick = {
                        coroutineScope.launch {
                            val polylineString = getPolyline(punto.longitud, punto.latitud)
                            if (polylineString != null) {
                                onRutaCalculada(polylineString)
                            } else {
                                Toast.makeText(context, "No se pudo obtener la ruta", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Ruta") }

                    Button(onClick = onFavoritar) { Text("Favoritar") }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = punto.nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = punto.descripcion, fontSize = 14.sp, color = Color.DarkGray)
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
                                "id" to JsonPrimitive(punto.id)
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

@Composable
private fun CircleLayerPersonales(
    puntosPersonales: List<PuntoPersonal>,
    onPuntoClick: (PuntoPersonal) -> Unit
) {
    if (puntosPersonales.isEmpty()) return

    val dotSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                puntosPersonales.map { punto ->
                    Feature(
                        geometry = Point(
                            Position(
                                punto.longitud.toDouble(),
                                punto.latitud.toDouble()
                            )
                        ),
                        properties = JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(punto.id),
                                "isHome" to JsonPrimitive(punto.isHome)
                            )
                        )
                    )
                }
            )
        )
    )

    CircleLayer(
        id = "circle-layer-personales",
        source = dotSource,
        color = const(Color(0xFF424242)),
        radius = const(7.dp),
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
                puntosPersonales.find { it.id == clickedId }?.let { onPuntoClick(it) }
            }
            ClickResult.Consume
        }
    )
}