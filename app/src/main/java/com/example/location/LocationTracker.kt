package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import com.example.security.AntiFakeLocationDetector
import com.example.security.SecurityCheckResult
import com.example.security.SecurityLevel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.graphics.Bitmap

data class LiveLocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val address: String = "Mencari alamat lokasi...",
    val plusCode: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDate: String = "",
    val formattedTime: String = "",
    val securityResult: SecurityCheckResult = SecurityCheckResult(
        isAuthentic = true,
        isMockDetected = false,
        securityLevel = SecurityLevel.AUTHENTIC_NORMAL,
        warnings = emptyList(),
        integrityHash = "INIT",
        signatureToken = "READY"
    ),
    val hasGpsFix: Boolean = false,
    val mapBitmap: Bitmap? = null
)

class LocationTracker(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationData = MutableStateFlow(createInitialState())
    val locationData: StateFlow<LiveLocationData> = _locationData.asStateFlow()

    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    private fun createInitialState(): LiveLocationData {
        val now = System.currentTimeMillis()
        val indonesianLocale = Locale("id", "ID")
        val dateFmt = SimpleDateFormat("dd MMM yyyy", indonesianLocale)
        val timeFmt = SimpleDateFormat("HH:mm", indonesianLocale)

        return LiveLocationData(
            formattedDate = dateFmt.format(Date(now)),
            formattedTime = timeFmt.format(Date(now))
        )
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking) return
        isTracking = true

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L
        ).apply {
            setMinUpdateIntervalMillis(1500L)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Also request last known location immediately
            fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    handleNewLocation(lastLoc)
                }
            }
        } catch (_: SecurityException) {
            // Permissions not yet granted
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun handleNewLocation(location: Location) {
        val secResult = AntiFakeLocationDetector.analyzeLocation(context, location)
        val indonesianLocale = Locale("id", "ID")
        val dateFmt = SimpleDateFormat("dd MMM yyyy", indonesianLocale)
        val timeFmt = SimpleDateFormat("HH:mm", indonesianLocale)
        val now = System.currentTimeMillis()

        val lat = location.latitude
        val lon = location.longitude

        _locationData.value = _locationData.value.copy(
            latitude = lat,
            longitude = lon,
            altitude = location.altitude,
            accuracy = location.accuracy,
            timestamp = now,
            formattedDate = dateFmt.format(Date(now)),
            formattedTime = timeFmt.format(Date(now)),
            securityResult = secResult,
            hasGpsFix = true
        )

        // Resolve geocoded address asynchronously
        scope.launch {
            val resolvedAddress = reverseGeocode(lat, lon)
            _locationData.value = _locationData.value.copy(
                address = resolvedAddress
            )
        }

        // Resolve real map tile for actual location coordinates
        scope.launch {
            val mapBmp = MapTileHelper.fetchLocationMapTile(context, lat, lon)
            _locationData.value = _locationData.value.copy(
                mapBitmap = mapBmp
            )
        }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale("id", "ID"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var addressLine = ""
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        addressLine = formatAddress(addresses[0])
                    }
                }
                // Allow a tiny moment for lambda callback if needed, or fallback below
                if (addressLine.isNotEmpty()) return@withContext addressLine
            }

            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            if (!list.isNullOrEmpty()) {
                return@withContext formatAddress(list[0])
            }
        } catch (_: Exception) {
            // Fallback when offline or Geocoder service unavailable
        }

        val latStr = String.format(Locale.US, "%.6f", lat)
        val lonStr = String.format(Locale.US, "%.6f", lon)
        return@withContext "Koordinat: $latStr, $lonStr (Alamat offline)"
    }

    private fun formatAddress(addr: Address): String {
        val parts = mutableListOf<String>()
        val thoroughfare = addr.thoroughfare ?: addr.featureName
        if (!thoroughfare.isNullOrBlank()) parts.add(thoroughfare)

        val subLocality = addr.subLocality
        if (!subLocality.isNullOrBlank()) parts.add(subLocality)

        val locality = addr.locality
        if (!locality.isNullOrBlank()) parts.add(locality)

        val adminArea = addr.adminArea
        if (!adminArea.isNullOrBlank()) parts.add(adminArea)

        val postalCode = addr.postalCode
        if (!postalCode.isNullOrBlank()) parts.add(postalCode)

        val country = addr.countryName ?: "Indonesia"
        parts.add(country)

        val joined = parts.distinct().joinToString(", ")
        return joined.ifEmpty {
            addr.getAddressLine(0) ?: "Lokasi Terpantau"
        }
    }
}
