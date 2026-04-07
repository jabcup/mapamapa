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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

// ─── Sistema de diseño ────────────────────────────────────────────────────────
// Paleta monocromática con un único acento funcional por rol semántico
private val ColorSurface        = Color(0xF5FFFFFF)   // card background
private val ColorSurfaceDark    = Color(0xFF111111)   // inversión para modo oscuro puntual
private val ColorBorder         = Color(0xFFE0E0E0)   // borde sutil
private val ColorBorderStrong   = Color(0xFF1A1A1A)   // borde de énfasis
private val ColorTextPrimary    = Color(0xFF0D0D0D)
private val ColorTextSecondary  = Color(0xFF757575)
private val ColorTextTertiary   = Color(0xFFB0B0B0)
private val ColorDestructive    = Color(0xFFD32F2F)
private val ColorRoute          = Color(0xFF1565C0)   // único color de acento: rutas
private val ColorRouteAlt       = Color(0xFFBF360C)   // rutas guardadas

private val COLOR_PUNTO_PERSONAL = "#1A1A1A"
private val COLOR_PUNTO_HOME     = "#0D47A1"

// ─── Modificadores reutilizables ──────────────────────────────────────────────
private fun Modifier.card(): Modifier = this
    .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color(0x14000000))
    .background(ColorSurface, RoundedCornerShape(20.dp))
    .clip(RoundedCornerShape(20.dp))

private fun Modifier.cardWithBorder(): Modifier = this
    .card()
    .border(1.dp, ColorBorder, RoundedCornerShape(20.dp))

// ─── Divider minimalista ──────────────────────────────────────────────────────
@Composable
private fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ColorBorder)
    )
}

// ─── Chip de etiqueta ─────────────────────────────────────────────────────────
@Composable
private fun TypeChip(label: String, dotColor: Color) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape)
        )
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
                    MapaScreen(
                        onVerPuntos    = { navController.navigate("puntos") },
                        onVerBarrios   = { navController.navigate("barrios") },
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
                    PersonalesScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MapaScreen(
    onVerPuntos:    () -> Unit,
    onVerBarrios:   () -> Unit,
    onVerFavoritos: () -> Unit
) {
    val context        = LocalContext.current
    val db             = remember { AppDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var permisoOtorgado by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permisoOtorgado = granted }

    LaunchedEffect(Unit) {
        if (!permisoOtorgado) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var modoOffline               by remember { mutableStateOf(false) }
    var puntos                    by remember { mutableStateOf<List<Punto>>(emptyList()) }
    var puntosPersonales          by remember { mutableStateOf<List<PuntoPersonal>>(emptyList()) }
    var puntoSeleccionado         by remember { mutableStateOf<Punto?>(null) }
    var puntoPersonalSeleccionado by remember { mutableStateOf<PuntoPersonal?>(null) }
    var puntoPersonalNuevo        by remember { mutableStateOf<Position?>(null) }
    var ruta                      by remember { mutableStateOf<String?>(null) }
    var rutasGuardadas            by remember { mutableStateOf<List<String>>(emptyList()) }
    var mostrarRutasGuardadas     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            puntos      = RetrofitClient.api.listarPuntos()
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
            } catch (dbEx: Exception) { dbEx.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        try { puntosPersonales = db.puntosPersonalesDao().getAll() }
        catch (e: Exception) { e.printStackTrace() }
    }

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
            Toast.makeText(context, "$guardados puntos esenciales guardados", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
                    home.longitud, home.latitud,
                    favorito.longitud, favorito.latitud
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
            rutasGuardadas        = emptyList()
        } else {
            val rutas = db.rutaDao().getAll()
            if (rutas.isEmpty()) {
                Toast.makeText(context, "No hay rutas guardadas", Toast.LENGTH_SHORT).show()
                return
            }
            rutasGuardadas        = rutas.map { it.polyline }
            mostrarRutasGuardadas = true
            Toast.makeText(context, "${rutas.size} rutas cargadas", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Mapa ─────────────────────────────────────────────────────────────
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
                            FeatureCollection(listOf(
                                Feature(geometry = LineString(posiciones), properties = JsonObject(emptyMap()))
                            ))
                        )
                    )
                    LineLayer(
                        id     = "ruta-layer",
                        source = rutaSource,
                        color  = const(ColorRoute),
                        width  = const(4.dp)
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
                        color  = const(ColorRouteAlt),
                        width  = const(3.dp)
                    )
                }
            }
        }

        // ── Banner offline ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = modoOffline,
            enter   = slideInVertically { -it } + fadeIn(),
            exit    = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .cardWithBorder()
                    .background(ColorSurface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).background(ColorDestructive, CircleShape))
                Text(
                    text       = "Sin conexión · mostrando favoritos",
                    color      = ColorTextPrimary,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.1.sp
                )
            }
        }

        val topPadding = if (modoOffline) 60.dp else 16.dp

        // ── Card punto remoto ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = puntoSeleccionado != null,
            enter    = slideInVertically { -it / 2 } + fadeIn(tween(200)),
            exit     = slideOutVertically { -it / 2 } + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding, start = 14.dp, end = 14.dp)
        ) {
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
                            Toast.makeText(context, "${punto.nombre} en favoritos", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // ── Card punto personal existente ────────────────────────────────────
        AnimatedVisibility(
            visible  = puntoPersonalSeleccionado != null,
            enter    = slideInVertically { -it / 2 } + fadeIn(tween(200)),
            exit     = slideOutVertically { -it / 2 } + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding, start = 14.dp, end = 14.dp)
        ) {
            puntoPersonalSeleccionado?.let { personal ->
                PuntoPersonalInfoCard(
                    puntoPersonal = personal,
                    onDismiss     = { puntoPersonalSeleccionado = null },
                    onEliminar    = {
                        coroutineScope.launch {
                            db.puntosPersonalesDao().delete(personal)
                            puntosPersonales          = db.puntosPersonalesDao().getAll()
                            puntoPersonalSeleccionado = null
                            Toast.makeText(context, "${personal.nombre} eliminado", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // ── Card nuevo punto personal ────────────────────────────────────────
        AnimatedVisibility(
            visible  = puntoPersonalNuevo != null,
            enter    = slideInVertically { -it / 2 } + fadeIn(tween(200)),
            exit     = slideOutVertically { -it / 2 } + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding, start = 14.dp, end = 14.dp)
        ) {
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
                    }
                )
            }
        }

        // ── Menú radial ──────────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp, end = 20.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            RadialMenuButton(
                items = listOf(
                    RadialMenuItem(0, Icons.Default.Place,      "Puntos",      Color(0xFF212121)),
                    RadialMenuItem(1, Icons.Default.Person,     "Personales",  Color(0xFF424242)),
                    RadialMenuItem(2, Icons.Default.Star,       "Favoritos",   Color(0xFF616161)),
                    RadialMenuItem(3, Icons.Default.Directions, "Calc. rutas", Color(0xFF757575)),
                    RadialMenuItem(4, Icons.Default.Download,   "Esenciales",  Color(0xFF9E9E9E)),
                    RadialMenuItem(5, Icons.Default.Layers,     "Ver rutas",
                        if (mostrarRutasGuardadas) Color(0xFF1565C0) else Color(0xFFBDBDBD))
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

// ─────────────────────────────────────────────────────────────────────────────
// Card: punto personal existente — minimalista
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PuntoPersonalInfoCard(
    puntoPersonal: PuntoPersonal,
    onDismiss:     () -> Unit,
    onEliminar:    () -> Unit,
    modifier:      Modifier = Modifier
) {
    val isHome    = puntoPersonal.isHome
    val chipColor = if (isHome) Color(COLOR_PUNTO_HOME.toColorInt()) else ColorTextTertiary

    Box(modifier = modifier.fillMaxWidth().cardWithBorder().padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // Encabezado
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TypeChip(
                    label    = if (isHome) "Hogar" else "Personal",
                    dotColor = chipColor
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint               = ColorTextTertiary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Nombre
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isHome) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint               = Color(COLOR_PUNTO_HOME.toColorInt()),
                        modifier           = Modifier.size(16.dp)
                    )
                }
                Text(
                    text       = puntoPersonal.nombre,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = ColorTextPrimary,
                    letterSpacing = (-0.3).sp
                )
            }

            if (puntoPersonal.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text      = puntoPersonal.descripcion,
                    fontSize  = 13.sp,
                    color     = ColorTextSecondary,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            ThinDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Acción destructiva
            Row(
                modifier  = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x08D32F2F))
                    .border(1.dp, Color(0x22D32F2F), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint               = ColorDestructive,
                    modifier           = Modifier.size(15.dp)
                )
                Text(
                    text       = "Eliminar punto",
                    fontSize   = 13.sp,
                    color      = ColorDestructive,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEliminar, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint               = ColorDestructive,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card: nuevo punto personal — minimalista
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

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = ColorBorderStrong,
        unfocusedBorderColor = ColorBorder,
        focusedLabelColor    = ColorTextPrimary,
        unfocusedLabelColor  = ColorTextSecondary,
        focusedTextColor     = ColorTextPrimary,
        unfocusedTextColor   = ColorTextPrimary,
        cursorColor          = ColorTextPrimary
    )

    Box(modifier = modifier.fillMaxWidth().cardWithBorder().padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // Encabezado
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TypeChip(label = "Nuevo punto", dotColor = ColorTextTertiary)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint               = ColorTextTertiary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value         = nombre,
                onValueChange = { nombre = it },
                label         = { Text("Nombre", fontSize = 13.sp) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp),
                colors        = fieldColors
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value         = descripcion,
                onValueChange = { descripcion = it },
                label         = { Text("Descripción", fontSize = 13.sp) },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 2,
                shape         = RoundedCornerShape(10.dp),
                colors        = fieldColors
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Toggle hogar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isHome) Color(COLOR_PUNTO_HOME.toColorInt()).copy(alpha = 0.06f)
                        else Color(0x05000000),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (isHome) Color(COLOR_PUNTO_HOME.toColorInt()).copy(alpha = 0.25f)
                        else ColorBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = if (isHome) Color(COLOR_PUNTO_HOME.toColorInt()) else ColorTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text       = "Marcar como hogar",
                        fontSize   = 13.sp,
                        color      = if (isHome) Color(COLOR_PUNTO_HOME.toColorInt()) else ColorTextSecondary,
                        fontWeight = if (isHome) FontWeight.Medium else FontWeight.Normal
                    )
                }
                Switch(
                    checked         = isHome,
                    onCheckedChange = { isHome = it },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = Color(COLOR_PUNTO_HOME.toColorInt()),
                        uncheckedThumbColor = ColorTextTertiary,
                        uncheckedTrackColor = ColorBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            ThinDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Botón guardar
            Button(
                onClick  = { if (nombre.isNotBlank()) onGuardar(nombre, descripcion, isHome) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = ColorSurfaceDark,
                    contentColor   = Color.White,
                    disabledContainerColor = ColorBorder,
                    disabledContentColor   = ColorTextTertiary
                ),
                enabled  = nombre.isNotBlank()
            ) {
                Text(
                    text       = "Guardar punto",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card: punto remoto / favorito — minimalista
// ─────────────────────────────────────────────────────────────────────────────
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
    var isLoading      by remember { mutableStateOf(false) }
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

    val colorTipo = try { Color(punto.tipo.color.toColorInt()) } catch (e: Exception) { Color.Gray }

    Box(modifier = modifier.fillMaxWidth().cardWithBorder().padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // Encabezado: tipo + cierre
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TypeChip(label = punto.tipo.nombre, dotColor = colorTipo)
                    if (modoOffline) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x0FFFCA28), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0x33FFCA28), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text("guardado", fontSize = 9.sp, color = Color(0xFFBCA002), letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint               = ColorTextTertiary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Nombre y descripción
            Text(
                text          = punto.nombre,
                fontSize      = 18.sp,
                fontWeight    = FontWeight.SemiBold,
                color         = ColorTextPrimary,
                letterSpacing = (-0.3).sp
            )
            if (punto.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text       = punto.descripcion,
                    fontSize   = 13.sp,
                    color      = ColorTextSecondary,
                    lineHeight = 18.sp
                )
            }

            // Acciones (solo si no es offline)
            if (!modoOffline) {
                Spacer(modifier = Modifier.height(16.dp))
                ThinDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón Ruta
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val poly = getPolyline(punto.longitud, punto.latitud)
                                if (poly != null) onRutaCalculada(poly)
                                else Toast.makeText(context, "No se pudo obtener la ruta", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderStrong),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = ColorTextPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(14.dp),
                                color     = ColorTextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Directions, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Ruta", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Botón Favoritar
                    Button(
                        onClick  = onFavoritar,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape  = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorSurfaceDark,
                            contentColor   = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Guardar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layers (sin cambios)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CircleLayerPlacitas(
    puntos:       List<Punto>,
    onPuntoClick: (Punto) -> Unit
) {
    if (puntos.isEmpty()) return
    val dotSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(puntos.map { punto ->
                Feature(
                    geometry   = Point(Position(punto.latitud.toDouble(), punto.longitud.toDouble())),
                    properties = JsonObject(mapOf(
                        "color" to JsonPrimitive(punto.tipo.color),
                        "id"    to JsonPrimitive(punto.id)
                    ))
                )
            })
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
            FeatureCollection(puntosPersonales.map { punto ->
                Feature(
                    geometry   = Point(Position(punto.latitud.toDouble(), punto.longitud.toDouble())),
                    properties = JsonObject(mapOf(
                        "id"     to JsonPrimitive(punto.id),
                        "color"  to JsonPrimitive(if (punto.isHome) COLOR_PUNTO_HOME else COLOR_PUNTO_PERSONAL),
                        "isHome" to JsonPrimitive(punto.isHome)
                    ))
                )
            })
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