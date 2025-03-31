package com.example.regresoacasa

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places

class MapsMarkerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        Log.d("MapsMarkerActivity", "onCreate: Iniciando la actividad")

        // Inicializar Places API con la API Key
        val apiKey = getString(R.string.google_maps_key)  // Obtiene la clave del archivo de recursos
        if (apiKey.isEmpty()) {
            Log.e("MapsMarkerActivity", "API Key no encontrada en resources")
            return
        }

        if (!Places.isInitialized()) {
            Log.d("MapsMarkerActivity", "Inicializando Places API")
            Places.initialize(applicationContext, apiKey)
        }

        val placesClient = Places.createClient(this)

        // Inicializar el fragmento del mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        if (mapFragment == null) {
            Log.e("MapsMarkerActivity", "No se encontró el fragmento de mapa")
        } else {
            Log.d("MapsMarkerActivity", "Obteniendo el mapa de forma asíncrona")
            mapFragment.getMapAsync(this)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d("MapsMarkerActivity", "El mapa está listo")
        googleMap = map

        // Verificar y solicitar permisos antes de habilitar la ubicación
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            Log.d("MapsMarkerActivity", "Permiso de ubicación concedido, habilitando ubicación")
        } else {
            Log.w("MapsMarkerActivity", "Permiso de ubicación NO concedido, solicitando permiso")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        // Agregar un callback para verificar si el mapa se carga correctamente
        googleMap.setOnMapLoadedCallback {
            Log.d("MapsMarkerActivity", "El mapa se ha cargado correctamente")
        }

        // Agregar un marcador en una ubicación de prueba
        val mexicoCity = LatLng(19.4326, -99.1332)
        googleMap.addMarker(MarkerOptions().position(mexicoCity).title("Ciudad de México"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mexicoCity, 12f))

        Log.d("MapsMarkerActivity", "Marcador añadido en Ciudad de México")
    }

    // Manejar la respuesta de la solicitud de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MapsMarkerActivity", "Permiso de ubicación concedido por el usuario")
                googleMap.isMyLocationEnabled = true
            } else {
                Log.e("MapsMarkerActivity", "Permiso de ubicación denegado")
            }
        }
    }
}
