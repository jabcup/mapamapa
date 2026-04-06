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

// ─── Colores para puntos personales ──────────────────────────────────────────
private val COLOR_PUNTO_PERSONAL = "#424242"
private val COLOR_PUNTO_HOME     = "#E65100"   // naranja intenso para distinguir el hogar

@Composable
fun MapaScreen(
    onVerPuntos: () -> Unit,
    onVerBarrios: () -> Unit,
    onVerFavoritos: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    // Estado de conexión: null = todavía cargando, true = online, false = offline
    var modoOffline by remember { mutableStateOf(false) }

    // Puntos remotos (o favoritos como fallback)
    var puntos by remember { mutableStateOf<List<Punto>>(emptyList()) }

    // Puntos personales locales
    var puntosPersonales by remember { mutableStateOf<List<PuntoPersonal>>(emptyList()) }

    // Selección activa — solo una puede ser no-null a la vez
    var puntoSeleccionado by remember { mutableStateOf<Punto?>(null) }
    var puntoPersonalSeleccionado by remember { mutableStateOf<PuntoPersonal?>(null) }
    var puntoPersonalNuevo by remember { mutableStateOf<Position?>(null) }

    var ruta by remember { mutableStateOf<String?>(null) }

    // ── Carga de puntos: intenta remoto, cae a favoritos locales ────────────
    LaunchedEffect(Unit) {
        try {
            val remotos = RetrofitClient.api.listarPuntos()
            puntos = remotos
            modoOffline = false
        } catch (e: Exception) {
            e.printStackTrace()
            modoOffline = true
            // Fallback: convierte los favoritos guardados en Punto para reutilizar el layer
            try {
                val favoritos = db.favoritosDao().getAll()
// ✅ DESPUÉS
                puntos = favoritos.map { fav ->
                    Punto(
                        id          = fav.id,
                        nombre      = fav.nombre,
                        descripcion = fav.descripcion,
                        latitud     = fav.latitud,
                        longitud    = fav.longitud,
                        tipo_id     = 0, // no se usa localmente, valor placeholder
                        tipo        = Tipo(id = 0, nombre = fav.tipo_nombre, color = fav.tipo_color)
                    )
                }
            } catch (dbEx: Exception) {
                dbEx.printStackTrace()
            }
        }
    }

    // ── Carga de puntos personales ────────────────────────────────────────────
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
        if (!hasPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        MaplibreMap(
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState,
            onMapClick = { position, _ ->
                // Toque en mapa vacío: abrir card para nuevo punto personal
                // (solo si no hay ninguna card abierta)
                if (puntoSeleccionado == null && puntoPersonalSeleccionado == null) {
                    puntoPersonalNuevo = position
                } else {
                    puntoSeleccionado = null
                    puntoPersonalSeleccionado = null
                    ruta = null
                }
                ClickResult.Pass
            }
        ) {
            locationState.location?.let { location ->
                val coords = Position(
                    latitude  = location.position.latitude,
                    longitude = location.position.longitude
                )
                LaunchedEffect(coords) {
                    cameraState.animateTo(CameraPosition(target = coords, zoom = 15.0))
                }
                LocationPuck(
                    idPrefix      = "user-location",
                    locationState = locationState,
                    cameraState   = cameraState
                )
            }

            // Layer de puntos remotos (o favoritos en offline)
            CircleLayerPlacitas(
                puntos = puntos,
                onPuntoClick = { punto ->
                    puntoPersonalNuevo        = null
                    puntoPersonalSeleccionado = null
                    puntoSeleccionado         = punto
                }
            )

            // Layer de puntos personales (home en naranja, resto en gris oscuro)
            CircleLayerPersonales(
                puntosPersonales = puntosPersonales,
                onPuntoClick = { personal ->
                    puntoPersonalNuevo = null
                    puntoSeleccionado  = null
                    ruta               = null
                    puntoPersonalSeleccionado = personal
                }
            )

            // Ruta calculada
            ruta?.let { encodedPolyline ->
                val posiciones = remember(encodedPolyline) { decodificarPolyline(encodedPolyline) }
                if (posiciones.size >= 2) {
                    val rutaSource = rememberGeoJsonSource(
                        data = GeoJsonData.Features(
                            FeatureCollection(
                                listOf(
                                    Feature(
                                        geometry   = LineString(posiciones),
                                        properties = JsonObject(emptyMap())
                                    )
                                )
                            )
                        )
                    )
                    LineLayer(
                        id     = "ruta-layer",
                        source = rutaSource,
                        color  = const(Color(0xFF1976D2)),
                        width  = const(5.dp)
                    )
                }
            }
        }

        // ── Banner de modo offline ───────────────────────────────────────────
        if (modoOffline) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                shape  = RoundedCornerShape(20.dp),
                color  = Color(0xFFB71C1C).copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Warning,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(16.dp)
                    )
                    Text(
                        text      = "Sin conexión — mostrando favoritos guardados",
                        color     = Color.White,
                        fontSize  = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Card de punto remoto / favorito ─────────────────────────────────
        puntoSeleccionado?.let { punto ->
            PuntoInfoCard(
                punto          = punto,
                modoOffline    = modoOffline,
                onDismiss      = { puntoSeleccionado = null; ruta = null },
                onRutaCalculada = { poly -> ruta = poly },
                onFavoritar    = {
                    coroutineScope.launch {
                        db.favoritosDao().insert(
                            PuntoFavorito(
                                id          = punto.id,
                                nombre      = punto.nombre,
                                descripcion = punto.descripcion,
                                latitud     = punto.latitud,
                                longitud    = punto.longitud,
                                tipo_nombre = punto.tipo.nombre,
                                tipo_color  = punto.tipo.color
                            )
                        )
                        Toast.makeText(context, "${punto.nombre} agregado a favoritos", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (modoOffline) 52.dp else 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ── Card de punto personal existente ────────────────────────────────
        puntoPersonalSeleccionado?.let { personal ->
            PuntoPersonalInfoCard(
                puntoPersonal = personal,
                onDismiss     = { puntoPersonalSeleccionado = null },
                onEliminar    = {
                    coroutineScope.launch {
                        db.puntosPersonalesDao().delete(personal)
                        puntosPersonales = db.puntosPersonalesDao().getAll()
                        puntoPersonalSeleccionado = null
                        Toast.makeText(context, "${personal.nombre} eliminado", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (modoOffline) 52.dp else 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ── Card para crear nuevo punto personal ────────────────────────────
        puntoPersonalNuevo?.let { posicion ->
            PuntoPersonalCard(
                posicion  = posicion,
                onDismiss = { puntoPersonalNuevo = null },
                onGuardar = { nombre, descripcion, isHome ->
                    coroutineScope.launch {
                        if (isHome) {
                            db.puntosPersonalesDao().setAsHome(
                                PuntoPersonal(
                                    nombre      = nombre,
                                    descripcion = descripcion,
                                    latitud     = posicion.latitude.toString(),
                                    longitud    = posicion.longitude.toString(),
                                    isHome      = true
                                )
                            )
                        } else {
                            db.puntosPersonalesDao().insert(
                                PuntoPersonal(
                                    nombre      = nombre,
                                    descripcion = descripcion,
                                    latitud     = posicion.latitude.toString(),
                                    longitud    = posicion.longitude.toString()
                                )
                            )
                        }
                        puntosPersonales = db.puntosPersonalesDao().getAll()
                        Toast.makeText(context, "$nombre guardado", Toast.LENGTH_SHORT).show()
                        puntoPersonalNuevo = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (modoOffline) 52.dp else 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ── Menú radial ──────────────────────────────────────────────────────
        Box(
            modifier        = Modifier.fillMaxSize().padding(4.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(modifier = Modifier.offset(x = 8.dp, y = 18.dp)) {
                RadialMenuButton(
                    items = listOf(
                        RadialMenuItem(0, Icons.Default.LocationOn, "Puntos",    Color.Cyan),
                        RadialMenuItem(1, Icons.Default.Call,       "Barrios",   Color.Green),
                        RadialMenuItem(2, Icons.Default.Star,       "Favoritos", Color.Yellow)
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

// ─────────────────────────────────────────────────────────────────────────────
// Card informativa de un punto personal EXISTENTE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PuntoPersonalInfoCard(
    puntoPersonal: PuntoPersonal,
    onDismiss:     () -> Unit,
    onEliminar:    () -> Unit,
    modifier:      Modifier = Modifier
) {
    val colorIndicador = if (puntoPersonal.isHome)
        Color(COLOR_PUNTO_HOME.toColorInt())
    else
        Color(COLOR_PUNTO_PERSONAL.toColorInt())

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Encabezado con tipo y botón cerrar
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(colorIndicador, CircleShape)
                    )
                    Text(
                        text       = if (puntoPersonal.isHome) "Mi hogar" else "Punto personal",
                        fontSize   = 12.sp,
                        color      = colorIndicador,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Nombre con ícono de hogar si aplica
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (puntoPersonal.isHome) {
                    Icon(
                        imageVector        = Icons.Default.Home,
                        contentDescription = "Hogar",
                        tint               = colorIndicador,
                        modifier           = Modifier.size(20.dp)
                    )
                }
                Text(
                    text       = puntoPersonal.nombre,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.Black
                )
            }

            if (puntoPersonal.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = puntoPersonal.descripcion,
                    fontSize = 14.sp,
                    color    = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón eliminar
            OutlinedButton(
                onClick = onEliminar,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
            ) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = null,
                    modifier           = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Eliminar punto")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card para CREAR un nuevo punto personal (toque en mapa vacío)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PuntoPersonalCard(
    posicion:  Position,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String, descripcion: String, isHome: Boolean) -> Unit,
    modifier:  Modifier = Modifier
) {
    var nombre      by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var isHome      by remember { mutableStateOf(false) }

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Nuevo punto personal",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.Black
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value         = nombre,
                onValueChange = { nombre = it },
                label         = { Text("Nombre") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value         = descripcion,
                onValueChange = { descripcion = it },
                label         = { Text("Descripción") },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(checked = isHome, onCheckedChange = { isHome = it })
                Text(text = "Marcar como hogar", fontSize = 14.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick  = { if (nombre.isNotBlank()) onGuardar(nombre, descripcion, isHome) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card informativa de un punto remoto / favorito
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PuntoInfoCard(
    punto:           Punto,
    modoOffline:     Boolean = false,
    onDismiss:       () -> Unit,
    onRutaCalculada: (String) -> Unit,
    onFavoritar:     () -> Unit,
    modifier:        Modifier = Modifier
) {
    val locationProvider = rememberDefaultLocationProvider()
    val locationState    = rememberUserLocationState(locationProvider)

    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context        = LocalContext.current

    suspend fun getPolyline(lat: String, long: String): String? {
        if (isLoading) return null
        isLoading = true
        try {
            val userLat = locationState.location?.position?.latitude.toString()
            val userLng = locationState.location?.position?.longitude.toString()
            return RetrofitClient.api.obtenerPolylineSimple(userLat, userLng, lat, long)
        } catch (e: Exception) {
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
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(colorTipo, CircleShape)
                    )
                    Text(
                        text       = punto.tipo.nombre,
                        fontSize   = 12.sp,
                        color      = colorTipo,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Badge si es favorito guardado (modo offline)
                    if (modoOffline) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF9C4)
                        ) {
                            Text(
                                text     = "★ Favorito",
                                fontSize = 10.sp,
                                color    = Color(0xFFF57F17),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Ruta solo disponible con conexión
                    if (!modoOffline) {
                        Button(onClick = {
                            coroutineScope.launch {
                                val poly = getPolyline(punto.longitud, punto.latitud)
                                if (poly != null) onRutaCalculada(poly)
                                else Toast.makeText(context, "No se pudo obtener la ruta", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Ruta") }

                        Button(onClick = onFavoritar) { Text("Favoritar") }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = punto.nombre,      fontSize = 18.sp, fontWeight = FontWeight.Bold,  color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = punto.descripcion, fontSize = 14.sp, color = Color.DarkGray)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layer: puntos remotos / favoritos
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CircleLayerPlacitas(
    puntos:       List<Punto>,
    onPuntoClick: (Punto) -> Unit
) {
    if (puntos.isEmpty()) return

    val dotSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                puntos.map { punto ->
                    Feature(
                        geometry   = Point(
                            Position(punto.latitud.toDouble(), punto.longitud.toDouble())
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
        id          = "circle-layer-placitas",
        source      = dotSource,
        color       = feature["color"].convertToColor(),
        radius      = const(10.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        minZoom     = 10f,
        onClick     = { features ->
            val clickedId = features.firstOrNull()
                ?.properties?.get("id")?.toString()?.toIntOrNull()
            if (clickedId != null) {
                puntos.find { it.id == clickedId }?.let { onPuntoClick(it) }
            }
            ClickResult.Consume
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Layer: puntos personales (home = naranja, resto = gris oscuro)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CircleLayerPersonales(
    puntosPersonales: List<PuntoPersonal>,
    onPuntoClick:     (PuntoPersonal) -> Unit
) {
    if (puntosPersonales.isEmpty()) return

    val dotSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                puntosPersonales.map { punto ->
                    Feature(
                        geometry   = Point(
                            Position(punto.longitud.toDouble(), punto.latitud.toDouble())
                        ),
                        properties = JsonObject(
                            mapOf(
                                "id"    to JsonPrimitive(punto.id),
                                // Guardamos el color como propiedad para usarlo en el layer
                                "color" to JsonPrimitive(
                                    if (punto.isHome) COLOR_PUNTO_HOME else COLOR_PUNTO_PERSONAL
                                ),
                                "isHome" to JsonPrimitive(punto.isHome)
                            )
                        )
                    )
                }
            )
        )
    )

    CircleLayer(
        id          = "circle-layer-personales",
        source      = dotSource,
        // Reutilizamos feature["color"] igual que en el layer de placitas
        color       = feature["color"].convertToColor(),
        radius      = const(7.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        minZoom     = 10f,
        onClick     = { features ->
            val clickedId = features.firstOrNull()
                ?.properties?.get("id")?.toString()?.toIntOrNull()
            if (clickedId != null) {
                puntosPersonales.find { it.id == clickedId }?.let { onPuntoClick(it) }
            }
            ClickResult.Consume
        }
    )
}