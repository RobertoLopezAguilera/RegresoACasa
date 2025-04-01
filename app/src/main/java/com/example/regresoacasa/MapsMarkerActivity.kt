package com.example.regresoacasa

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MapsMarkerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val destination = LatLng(20.006686, -101.021450) // Santa Ana Maya, Michoacán 20.006686, -101.021450

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        Log.d("MapsMarkerActivity", "onCreate: Iniciando la actividad")

        val apiKey = getString(R.string.google_maps_key)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d("MapsMarkerActivity", "El mapa está listo")
        googleMap = map

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            getUserLocation()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun getUserLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 12f))
                Log.d("MapsMarkerActivity", "Ubicación actual: ${location.latitude}, ${location.longitude}")
                drawRoute(userLatLng, destination)
            } else {
                Log.e("MapsMarkerActivity", "No se pudo obtener la ubicación actual")
            }
        }
    }

    private fun drawRoute(origin: LatLng, destination: LatLng) {
        val apiKey = getString(R.string.google_maps_key)
        val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseData = response.body?.string()

                if (responseData != null) {
                    val jsonObject = JSONObject(responseData)
                    val routes = jsonObject.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val points = routes.getJSONObject(0)
                            .getJSONArray("legs")
                            .getJSONObject(0)
                            .getJSONArray("steps")

                        val polylineOptions = PolylineOptions().width(10f).color(ContextCompat.getColor(this@MapsMarkerActivity, R.color.purple_500)).geodesic(true)

                        for (i in 0 until points.length()) {
                            val polyline = points.getJSONObject(i).getJSONObject("polyline").getString("points")
                            polylineOptions.addAll(PolyUtil.decode(polyline))
                        }

                        runOnUiThread {
                            googleMap.addPolyline(polylineOptions)
                            googleMap.addMarker(MarkerOptions().position(destination).title("Casa en Santa Ana Maya"))
                        }
                    } else {
                        Log.e("MapsMarkerActivity", "No se encontraron rutas")
                    }
                }
            } catch (e: Exception) {
                Log.e("MapsMarkerActivity", "Error al obtener la ruta: ${e.message}")
            }
        }
    }
}
