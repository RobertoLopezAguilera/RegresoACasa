package com.example.regresoacasa

import android.Manifest
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.compose.foundation.shape.RoundedCornerShape


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    var cameraPositionState = rememberCameraPositionState()
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var polylinePoints by remember { mutableStateOf(listOf<LatLng>()) }

    var query by remember { mutableStateOf(TextFieldValue("")) }
    var suggestions by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    LaunchedEffect(Unit) {
        if (!locationPermission.status.isGranted) {
            locationPermission.launchPermissionRequest()
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    userLocation = latLng
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.text.length > 2) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val apiKey = context.getString(R.string.google_maps_key)
                            val url =
                                "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=${it.text}&key=$apiKey&components=country:mx"

                            val request = Request.Builder().url(url).build()
                            val response = OkHttpClient().newCall(request).execute()
                            val responseBody = response.body?.string()

                            if (!responseBody.isNullOrEmpty()) {
                                val json = JSONObject(responseBody)
                                val predictions = json.getJSONArray("predictions")
                                val results = mutableListOf<Pair<String, String>>()
                                for (i in 0 until predictions.length()) {
                                    val item = predictions.getJSONObject(i)
                                    results.add(
                                        item.getString("description") to item.getString("place_id")
                                    )
                                }
                                suggestions = results
                            }
                        } catch (e: Exception) {
                            Log.e("AutoComplete", "Error: ${e.message}")
                        }
                    }
                } else {
                    suggestions = emptyList()
                }
            },
            label = { Text("Buscar dirección") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        LazyColumn {
            items(suggestions) { suggestion ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            query = TextFieldValue(suggestion.first)
                            suggestions = emptyList()

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val apiKey = context.getString(R.string.google_maps_key)
                                    val url =
                                        "https://maps.googleapis.com/maps/api/place/details/json?place_id=${suggestion.second}&key=$apiKey"

                                    val request = Request.Builder().url(url).build()
                                    val response = OkHttpClient().newCall(request).execute()
                                    val responseBody = response.body?.string()

                                    if (!responseBody.isNullOrEmpty()) {
                                        val json = JSONObject(responseBody)
                                        val location =
                                            json.getJSONObject("result")
                                                .getJSONObject("geometry")
                                                .getJSONObject("location")
                                        val lat = location.getDouble("lat")
                                        val lng = location.getDouble("lng")

                                        destination = LatLng(lat, lng)

                                        userLocation?.let { origen ->
                                            drawRoute(
                                                origen,
                                                destination!!,
                                                context.getString(R.string.google_maps_key)
                                            ) {
                                                polylinePoints = it
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("PlaceDetails", "Error: ${e.message}")
                                }
                            }
                        }
                ) {
                    Text(
                        text = suggestion.first,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        GoogleMap(
            modifier = Modifier.weight(1f),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermission.status.isGranted),
            uiSettings = MapUiSettings(myLocationButtonEnabled = true)
        ) {
            userLocation?.let {
                Marker(
                    state = rememberMarkerState(position = it),
                    title = "Tu ubicación"
                )
            }

            destination?.let {
                Marker(
                    state = rememberMarkerState(position = it),
                    title = "Destino"
                )
            }

            if (polylinePoints.isNotEmpty()) {
                Polyline(points = polylinePoints)
            }
        }
    }
}


private fun drawRoute(
    origen: LatLng,
    destino: LatLng,
    apiKey: String,
    onResult: (List<LatLng>) -> Unit
) {
    try {
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${origen.latitude},${origen.longitude}&" +
                "destination=${destino.latitude},${destino.longitude}&key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val responseData = response.body?.string()

        if (!responseData.isNullOrEmpty()) {
            val json = JSONObject(responseData)
            val pointsArray = json
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONArray("legs")
                .getJSONObject(0)
                .getJSONArray("steps")

            val options = mutableListOf<LatLng>()
            for (i in 0 until pointsArray.length()) {
                val poly = pointsArray.getJSONObject(i)
                    .getJSONObject("polyline")
                    .getString("points")
                options.addAll(PolyUtil.decode(poly))
            }
            onResult(options)
        }
    } catch (e: Exception) {
        Log.e("RouteError", "Error: ${e.message}")
    }
}
