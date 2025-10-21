package com.example.agristockcapstoneproject

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.agristockcapstoneproject.databinding.ActivityMapPreviewBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptorFactory

class MapPreviewActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapPreviewBinding
    private lateinit var map: GoogleMap
    private var location: LatLng? = null
    private var address: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        // Get location data from intent
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        address = intent.getStringExtra("address")

        if (latitude != 0.0 && longitude != 0.0) {
            location = LatLng(latitude, longitude)
        } else {
            Toast.makeText(this, "No location data provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupMap()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Set address text
        binding.tvAddress.text = address ?: "Unknown location"
    }

    private fun setupMap() {
        try {
            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this)
            android.util.Log.d("MapPreviewActivity", "Map fragment setup successful")
        } catch (e: Exception) {
            android.util.Log.e("MapPreviewActivity", "Error setting up map: ${e.message}")
            Toast.makeText(this, "Error setting up map: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        
        try {
            // Enable zoom controls and gestures
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isZoomGesturesEnabled = true
            map.uiSettings.isScrollGesturesEnabled = true
            map.uiSettings.isTiltGesturesEnabled = true
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isCompassEnabled = true
            
            // Set map type to satellite view
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            
            // Set location if available
            location?.let { latLng ->
                // Add marker at location
                map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Location")
                        .snippet(address)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
                
                // Move camera to location
                val cameraPosition = CameraPosition.Builder()
                    .target(latLng)
                    .zoom(16f)
                    .tilt(45f)
                    .bearing(0f)
                    .build()
                
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                
                android.util.Log.d("MapPreviewActivity", "Map initialized successfully with satellite view for location: $latLng")
            } ?: run {
                android.util.Log.w("MapPreviewActivity", "No location provided")
                Toast.makeText(this, "No location to display", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MapPreviewActivity", "Error initializing map: ${e.message}")
            Toast.makeText(this, "Error initializing map: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}