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
                            onVerPuntos    = { navController.navigate("puntos") },
                            onVerBarrios   = { navController.navigate("barrios") },
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
                composable("barrios") {
                    PersonalesScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private val COLOR_PUNTO_PERSONAL = "#424242"
private val COLOR_PUNTO_HOME     = "#E65100"

@Composable
fun MapaScreen(
    onVerPuntos: () -> Unit,
    onVerBarrios: () -> Unit,
    onVerFavoritos: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    // ── Permiso: se verifica antes de llamar a rememberDefaultLocationProvider ──
    var permisoOtorgado by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permisoOtorgado = granted
    }

    LaunchedEffect(Unit) {
        if (!permisoOtorgado) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var modoOffline by remember { mutableStateOf(false) }
    var puntos by remember { mutableStateOf<List<Punto>>(emptyList()) }
    var puntosPersonales by remember { mutableStateOf<List<PuntoPersonal>>(emptyList()) }
    var puntoSeleccionado by remember { mutableStateOf<Punto?>(null) }
    var puntoPersonalSeleccionado by remember { mutableStateOf<PuntoPersonal?>(null) }
    var puntoPersonalNuevo by remember { mutableStateOf<Position?>(null) }
    var ruta by remember { mutableStateOf<String?>(null) }
    var rutasGuardadas by remember { mutableStateOf<List<String>>(emptyList()) }
    var mostrarRutasGuardadas by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            puntos = RetrofitClient.api.listarPuntos()
            modoOffline = false
        } catch (e: Exception) {
            e.printStackTrace()
            modoOffline = true
            try {
                val favoritos = db.favoritosDao().getAll()
                puntos = favoritos.map { fav ->
                    Punto(
                        id          = fav.id,
                        nombre      = fav.nombre,
                        descripcion = fav.descripcion,
                        latitud     = fav.latitud,
                        longitud    = fav.longitud,
                        tipo_id     = 0,
                        tipo        = Tipo(id = 0, nombre = fav.tipo_nombre, color = fav.tipo_color)
                    )
                }
            } catch (dbEx: Exception) {
                dbEx.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            puntosPersonales = db.puntosPersonalesDao().getAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Location: solo se inicializa si el permiso fue otorgado ─────────────
    val locationProvider = if (permisoOtorgado) rememberDefaultLocationProvider() else null
    val locationState    = if (locationProvider != null) rememberUserLocationState(locationProvider) else null

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                locationState?.location?.position?.longitude ?: -68.1193,
                locationState?.location?.position?.latitude ?: -16.5000
            ),
            zoom = 15.0
        )
    )

    suspend fun cargarPuntosEsencialesComoFavoritos() {
        try {
            val esenciales = RetrofitClient.api.listarPuntosEscenciales()
            var guardados = 0
            esenciales.forEach { punto ->
                db.favoritosDao().insert(
                    PuntoFavorito(
                        id          = punto.id,
                        nombre      = punto.nombre,
                        descripcion = punto.descripcion,
                        latitud     = punto.latitud,
                        longitud    = punto.longitud,
                        tipo_nombre = punto.tipo?.nombre ?: "Esencial",
                        tipo_color  = punto.tipo?.color ?: "#9E9E9E"
                    )
                )
                guardados++
            }
            Toast.makeText(context, "$guardados puntos esenciales guardados como favoritos", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.javaClass.simpleName} - ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    suspend fun calcularYGuardarRutas() {
        val home = db.puntosPersonalesDao().getHome()
        if (home == null) {
            Toast.makeText(context, "No tienes un punto hogar definido", Toast.LENGTH_SHORT).show()
            return
        }
        val favoritos = db.favoritosDao().getAll()
        if (favoritos.isEmpty()) {
            Toast.makeText(context, "No tienes favoritos guardados", Toast.LENGTH_SHORT).show()
            return
        }
        db.rutaDao().deleteAll()
        var calculadas = 0
        favoritos.forEach { favorito ->
            try {
                val polyline = RetrofitClient.api.obtenerPolylineSimple(
                    home.longitud,
                    home.latitud,
                    favorito.longitud,
                    favorito.latitud
                )
                if (polyline != null) {
                    db.rutaDao().insert(Ruta(polyline = polyline))
                    calculadas++
                }
            } catch (e: Exception) {
                println("Error calculando ruta a ${favorito.nombre}: ${e.message}")
            }
        }
        Toast.makeText(context, "$calculadas rutas calculadas y guardadas", Toast.LENGTH_SHORT).show()
    }

    suspend fun toggleRutasGuardadas() {
        if (mostrarRutasGuardadas) {
            mostrarRutasGuardadas = false
            rutasGuardadas = emptyList()
        } else {
            val rutas = db.rutaDao().getAll()
            if (rutas.isEmpty()) {
                Toast.makeText(context, "No hay rutas guardadas", Toast.LENGTH_SHORT).show()
                return
            }
            rutasGuardadas = rutas.map { it.polyline }
            mostrarRutasGuardadas = true
            Toast.makeText(context, "${rutas.size} rutas cargadas", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        MaplibreMap(
            baseStyle   = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState,
            onMapClick  = { position, _ ->
                if (puntoSeleccionado == null && puntoPersonalSeleccionado == null) {
                    puntoPersonalNuevo = position
                } else {
                    puntoSeleccionado         = null
                    puntoPersonalSeleccionado = null
                    ruta                      = null
                }
                ClickResult.Pass
            }
        ) {
            if (permisoOtorgado && locationState != null) {
                locationState.location?.let { location ->
                    val coords = Position(
                        latitude  = location.position.latitude,
                        longitude = location.position.longitude
                    )
                    // Solo centra la primera vez que se obtiene la ubicación
                    var centradoInicial by remember { mutableStateOf(false) }
                    LaunchedEffect(centradoInicial) {
                        if (!centradoInicial) {
                            cameraState.animateTo(CameraPosition(target = coords, zoom = 15.0))
                            centradoInicial = true
                        }
                    }
                    LocationPuck(
                        idPrefix      = "user-location",
                        locationState = locationState,
                        cameraState   = cameraState
                    )
                }
            }

            CircleLayerPlacitas(
                puntos       = puntos,
                onPuntoClick = { punto ->
                    puntoPersonalNuevo        = null
                    puntoPersonalSeleccionado = null
                    puntoSeleccionado         = punto
                }
            )

            CircleLayerPersonales(
                puntosPersonales = puntosPersonales,
                onPuntoClick     = { personal ->
                    puntoPersonalNuevo        = null
                    puntoSeleccionado         = null
                    ruta                      = null
                    puntoPersonalSeleccionado = personal
                }
            )

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

            if (mostrarRutasGuardadas && rutasGuardadas.isNotEmpty()) {
                val todasLasPosiciones = rutasGuardadas
                    .map { decodificarPolyline(it) }
                    .filter { it.size >= 2 }
                if (todasLasPosiciones.isNotEmpty()) {
                    val rutasSource = rememberGeoJsonSource(
                        data = GeoJsonData.Features(
                            FeatureCollection(
                                todasLasPosiciones.mapIndexed { index, posiciones ->
                                    Feature(
                                        geometry   = LineString(posiciones),
                                        properties = JsonObject(mapOf("index" to JsonPrimitive(index)))
                                    )
                                }
                            )
                        )
                    )
                    LineLayer(
                        id     = "rutas-guardadas-layer",
                        source = rutasSource,
                        color  = const(Color(0xFFE65100)),
                        width  = const(4.dp)
                    )
                }
            }
        }

        if (modoOffline) {
            Surface(
                modifier       = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                shape          = RoundedCornerShape(20.dp),
                color          = Color(0xFFB71C1C).copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(text = "Sin conexión — mostrando favoritos guardados", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        puntoSeleccionado?.let { punto ->
            PuntoInfoCard(
                punto           = punto,
                modoOffline     = modoOffline,
                locationState   = locationState,
                onDismiss       = { puntoSeleccionado = null; ruta = null },
                onRutaCalculada = { poly -> ruta = poly },
                onFavoritar     = {
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
                                    latitud     = posicion.longitude.toString(),
                                    longitud    = posicion.latitude.toString(),
                                    isHome      = true
                                )
                            )
                        } else {
                            db.puntosPersonalesDao().insert(
                                PuntoPersonal(
                                    nombre      = nombre,
                                    descripcion = descripcion,
                                    latitud     = posicion.longitude.toString(),
                                    longitud    = posicion.latitude.toString()
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

        Box(
            modifier         = Modifier.fillMaxSize().padding(4.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(modifier = Modifier.offset(x = 8.dp, y = 18.dp)) {
                RadialMenuButton(
                    items = listOf(
                        RadialMenuItem(0, Icons.Default.LocationOn, "Puntos",     Color.Cyan),
                        RadialMenuItem(1, Icons.Default.Call,       "Personales",    Color.Green),
                        RadialMenuItem(2, Icons.Default.Star,       "Favoritos",  Color.Yellow),
                        RadialMenuItem(3, Icons.Default.Clear,      "Rutas",      Color.Magenta),
                        RadialMenuItem(4, Icons.Default.Refresh,    "Cargar fav", Color.White),
                        RadialMenuItem(5, Icons.Default.List,       "Ver rutas",  if (mostrarRutasGuardadas) Color.Red else Color.LightGray)
                    ),
                    onItemSelected = { item ->
                        when (item.id) {
                            0 -> onVerPuntos()
                            1 -> onVerBarrios()
                            2 -> onVerFavoritos()
                            3 -> coroutineScope.launch { calcularYGuardarRutas() }
                            4 -> coroutineScope.launch { cargarPuntosEsencialesComoFavoritos() }
                            5 -> coroutineScope.launch { toggleRutasGuardadas() }
                        }
                    }
                )
            }
        }
    }
}

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
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(14.dp).background(colorIndicador, CircleShape))
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

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (puntoPersonal.isHome) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Hogar", tint = colorIndicador, modifier = Modifier.size(20.dp))
                }
                Text(text = puntoPersonal.nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            if (puntoPersonal.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = puntoPersonal.descripcion, fontSize = 14.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick  = onEliminar,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Eliminar punto")
            }
        }
    }
}

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
                Text(text = "Nuevo punto personal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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

@Composable
private fun PuntoInfoCard(
    punto:           Punto,
    modoOffline:     Boolean = false,
    locationState:   org.maplibre.compose.location.UserLocationState?,
    onDismiss:       () -> Unit,
    onRutaCalculada: (String) -> Unit,
    onFavoritar:     () -> Unit,
    modifier:        Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context        = LocalContext.current

    suspend fun getPolyline(lat: String, long: String): String? {
        if (isLoading) return null
        isLoading = true
        try {
            val userLat = locationState?.location?.position?.latitude.toString()
            val userLng = locationState?.location?.position?.longitude.toString()
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
                    Box(modifier = Modifier.size(14.dp).background(colorTipo, CircleShape))
                    Text(text = punto.tipo.nombre, fontSize = 12.sp, color = colorTipo, fontWeight = FontWeight.SemiBold)
                    if (modoOffline) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF9C4)) {
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
            Text(text = punto.nombre,      fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = punto.descripcion, fontSize = 14.sp, color = Color.DarkGray)
        }
    }
}

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
                        geometry   = Point(Position(punto.latitud.toDouble(), punto.longitud.toDouble())),
                        properties = JsonObject(mapOf(
                            "color" to JsonPrimitive(punto.tipo.color),
                            "id"    to JsonPrimitive(punto.id)
                        ))
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
            val clickedId = features.firstOrNull()?.properties?.get("id")?.toString()?.toIntOrNull()
            if (clickedId != null) puntos.find { it.id == clickedId }?.let { onPuntoClick(it) }
            ClickResult.Consume
        }
    )
}

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
                        geometry   = Point(Position(punto.latitud.toDouble(), punto.longitud.toDouble())),
                        properties = JsonObject(mapOf(
                            "id"     to JsonPrimitive(punto.id),
                            "color"  to JsonPrimitive(if (punto.isHome) COLOR_PUNTO_HOME else COLOR_PUNTO_PERSONAL),
                            "isHome" to JsonPrimitive(punto.isHome)
                        ))
                    )
                }
            )
        )
    )

    CircleLayer(
        id          = "circle-layer-personales",
        source      = dotSource,
        color       = feature["color"].convertToColor(),
        radius      = const(7.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        minZoom     = 10f,
        onClick     = { features ->
            val clickedId = features.firstOrNull()?.properties?.get("id")?.toString()?.toIntOrNull()
            if (clickedId != null) puntosPersonales.find { it.id == clickedId }?.let { onPuntoClick(it) }
            ClickResult.Consume
        }
    )
}