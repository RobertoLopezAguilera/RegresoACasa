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
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MapsMarkerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap //mapa
    private lateinit var fusedLocationClient: FusedLocationProviderClient //localizacion actual

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var destination: LatLng? = null // Almacenar destino seleccionado por el usuario

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        Log.d("MapsMarkerActivity", "onCreate: Iniciando la actividad")

        // API Key segura
        val apiKey = getString(R.string.google_maps_key)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        setupAutocompleteFragment()
    }

    //Carga el mapa
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
        //permisos de lastlocation
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 12f))
                Log.d("MapsMarkerActivity", "Ubicación actual: ${location.latitude}, ${location.longitude}")

                // Si ya hay un destino seleccionado, trazar la ruta
                destination?.let { drawRoute(userLatLng, it) }
            } else {
                Log.e("MapsMarkerActivity", "No se pudo obtener la ubicación actual")
            }
        }
    }

    //Widget de autocompletado
    private fun setupAutocompleteFragment() {
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                Log.i("MapsMarkerActivity", "Lugar seleccionado: ${place.name}, ID: ${place.id}")

                val placeLatLng = place.latLng
                if (placeLatLng != null) {
                    destination = placeLatLng // Guardar la nueva ubicación seleccionada
                    googleMap.clear() // Limpiar mapa antes de dibujar nueva ruta
                    googleMap.addMarker(MarkerOptions().position(placeLatLng).title(place.name))

                    getUserLocation() // Volver a obtener la ubicación del usuario y trazar la nueva ruta
                }
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                Log.e("MapsMarkerActivity", "Error en Autocomplete: $status")
            }
        })
    }

    //Polilinea
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

                        val polylineOptions = PolylineOptions().width(10f)
                            .color(ContextCompat.getColor(this@MapsMarkerActivity, R.color.purple_500))
                            .geodesic(true)

                        for (i in 0 until points.length()) {
                            val polyline = points.getJSONObject(i).getJSONObject("polyline").getString("points")
                            polylineOptions.addAll(PolyUtil.decode(polyline))
                        }

                        runOnUiThread {
                            googleMap.addPolyline(polylineOptions)
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
