package com.example.agristockcapstoneproject

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.agristockcapstoneproject.databinding.ActivityLocationPickerBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import java.io.IOException
import java.util.Locale

class LocationPickerActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private lateinit var binding: ActivityLocationPickerBinding
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLocation: LatLng? = null
    private var selectedMarker: Marker? = null
    private var geocoder: Geocoder? = null
    private var isMapReady = false
    private var isSearching = false

    // Location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getCurrentLocation()
            }
            else -> {
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure status bar for immersive experience
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        setupUI()
        setupMap()
        animateBottomSheet()
    }

    private fun setupUI() {
        // Back button with smooth animation
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Current location button with loading state
        binding.btnCurrentLocation.setOnClickListener {
            if (!isSearching) {
                requestLocationPermission()
            }
        }

        // Confirm location button
        binding.btnConfirmLocation.setOnClickListener {
            if (selectedLocation != null) {
                confirmLocation()
            } else {
                showToast("Please select a location on the map")
            }
        }

        // Search functionality
        binding.etSearchLocation.setOnEditorActionListener { _, _, _ ->
            val searchQuery = binding.etSearchLocation.text.toString().trim()
            if (searchQuery.isNotEmpty()) {
                searchLocation(searchQuery)
            }
            true
        }

        // Real-time search as user types
        binding.etSearchLocation.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                animateSearchBar()
            }
        }
    }

    private fun setupMap() {
        try {
            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this)
        } catch (e: Exception) {
            showMapErrorDialog()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        isMapReady = true
        
        try {
            // Configure map settings for modern experience
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isZoomGesturesEnabled = true
            map.uiSettings.isScrollGesturesEnabled = true
            map.uiSettings.isTiltGesturesEnabled = true
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isCompassEnabled = false
            
            // Set modern map style
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            
            // Set click listener
            map.setOnMapClickListener(this)
            
            // Set initial location
            setDefaultLocation()
            
            // Request current location
            requestLocationPermission()
            
        } catch (e: Exception) {
            showMapErrorDialog()
        }
    }

    private fun setDefaultLocation() {
        val defaultLocation = LatLng(14.5995, 120.9842) // Manila, Philippines
        val cameraPosition = CameraPosition.Builder()
            .target(defaultLocation)
            .zoom(15f)
            .tilt(0f)
            .bearing(0f)
            .build()
        
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        updateLocationDisplay("Tap on the map to select a location")
    }

    override fun onMapClick(latLng: LatLng) {
        selectedLocation = latLng
        updateMarker(latLng)
        reverseGeocode(latLng)
    }

    private fun updateMarker(latLng: LatLng) {
        // Remove existing marker
        selectedMarker?.remove()
        
        // Add new marker with custom styling
        selectedMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .title("Selected Location")
        )
        
        // Animate camera to selected location
        val cameraPosition = CameraPosition.Builder()
            .target(latLng)
            .zoom(16f)
            .tilt(0f)
            .bearing(0f)
            .build()
        
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun reverseGeocode(latLng: LatLng) {
        if (geocoder == null) {
            updateLocationDisplay("Location: ${latLng.latitude}, ${latLng.longitude}")
            return
        }

        try {
            val addresses = geocoder?.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val addressText = buildString {
                    address.getAddressLine(0)?.let { append(it) }
                    if (address.locality != null && address.countryName != null) {
                        append(" • ${address.locality}, ${address.countryName}")
                    }
                }
                updateLocationDisplay(addressText)
            } else {
                updateLocationDisplay("Location: ${latLng.latitude}, ${latLng.longitude}")
            }
        } catch (e: IOException) {
            updateLocationDisplay("Location: ${latLng.latitude}, ${latLng.longitude}")
        }
    }

    private fun updateLocationDisplay(address: String) {
        binding.tvSelectedAddress.text = address
        animateBottomSheet()
    }

    private fun searchLocation(query: String) {
        if (geocoder == null) {
            showToast("Search not available")
            return
        }

        setLoading(true)
        
        try {
            val addresses = geocoder?.getFromLocationName(query, 5)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                
                selectedLocation = latLng
                updateMarker(latLng)
                
                val addressText = buildString {
                    address.getAddressLine(0)?.let { append(it) }
                    if (address.locality != null && address.countryName != null) {
                        append(" • ${address.locality}, ${address.countryName}")
                    }
                }
                updateLocationDisplay(addressText)
            } else {
                showToast("Location not found")
            }
        } catch (e: IOException) {
            showToast("Search failed")
        } finally {
            setLoading(false)
        }
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun getCurrentLocation() {
        setLoading(true)
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && 
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setLoading(false)
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                setLoading(false)
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    selectedLocation = latLng
                    updateMarker(latLng)
                    reverseGeocode(latLng)
                } else {
                    showToast("Unable to get current location")
                }
            }
            .addOnFailureListener {
                setLoading(false)
                showToast("Failed to get current location")
            }
    }

    private fun confirmLocation() {
        if (selectedLocation != null) {
            val resultIntent = Intent()
            resultIntent.putExtra("latitude", selectedLocation!!.latitude)
            resultIntent.putExtra("longitude", selectedLocation!!.longitude)
            resultIntent.putExtra("address", binding.tvSelectedAddress.text.toString())
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun setLoading(loading: Boolean) {
        isSearching = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnCurrentLocation.isEnabled = !loading
        binding.btnConfirmLocation.isEnabled = !loading
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location permission to show your current location. Please grant permission in settings.")
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ -> 
                dialog.dismiss()
                setDefaultLocation()
            }
            .show()
    }

    private fun showPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Location Permission")
            .setMessage("This app needs location permission to show your current location on the map.")
            .setPositiveButton("Grant Permission") { _, _ ->
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Cancel") { dialog, _ -> 
                dialog.dismiss()
            }
            .show()
    }

    private fun showMapErrorDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Map Error")
            .setMessage("Unable to load the map. Please check your internet connection and try again.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Enter Location Manually") { _, _ ->
                binding.etSearchLocation.requestFocus()
            }
            .show()
    }

    // Animation methods
    private fun animateBottomSheet() {
        val animator = ObjectAnimator.ofFloat(binding.bottomSheet, "translationY", 200f, 0f)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    private fun animateSearchBar() {
        val animator = ObjectAnimator.ofFloat(binding.etSearchLocation, "scaleX", 1f, 1.02f)
        animator.duration = 200
        animator.repeatCount = 1
        animator.repeatMode = ValueAnimator.REVERSE
        animator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
    }
}