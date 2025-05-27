package com.example.sensorgps

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {
    // variables de ubicación
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    // inicia el proveedor de ubicación
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            // almacenan la latitud y longitud actual
            var lat by remember { mutableStateOf<Double?>(null) }
            var lon by remember { mutableStateOf<Double?>(null) }

            // verifica si ya está concedido los permisos
            val hasLocationPermission = remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                )
            }
            // solicita múltiples permisos
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                hasLocationPermission.value =
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            }

            // se obtiene la ubicación automática cuando se tiene el permiso
            LaunchedEffect(hasLocationPermission.value) {
                if (hasLocationPermission.value) {
                    val request = LocationRequest.Builder(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L
                    ).build()

                    locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { location ->
                                lat = location.latitude
                                lon = location.longitude
                            }
                        }
                    }
                    // cuando se obtiene una nueva ubicación se actualiza la latitud y longitud
                    try {
                        fusedClient.requestLocationUpdates(
                            request, locationCallback, mainLooper
                        )
                    } catch (_: SecurityException) {}
                }
                    // si los permisos no están concedidos, se solicitan nuevamente
                else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            // retener rastreo de ubicación al salir de la App
            DisposableEffect(Unit) {
                onDispose {
                    if (::locationCallback.isInitialized) {
                        fusedClient.removeLocationUpdates(locationCallback)
                    }
                }
            }

            AppScaffold(lat, lon)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(lat: Double?, lon: Double?) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ubicación actual") }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (lat != null && lon != null) {
                    OsmMapView(lat, lon)
                }
            }
        }
    }
}
// mapa con osmdroid
@Composable
fun OsmMapView(lat: Double, lon: Double) {
    val context = LocalContext.current

    AndroidView(factory = {
        // obtiene el contexto android para inicializar el mapa
        Configuration.getInstance().load(context, context.getSharedPreferences("osm", 0))
        // carga configuración de osmdroid, creación de mapa y su contenido
        val mapView = MapView(context)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val geoPoint = GeoPoint(lat, lon)
        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(geoPoint)
        // marcador
        val marker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)

        mapView
    }, modifier = Modifier
        .fillMaxSize())
}
