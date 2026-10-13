package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import com.example.security.AntiFakeLocationDetector
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

class LocationManager(private val context: Context, private val scope: CoroutineScope) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationData = MutableStateFlow(LiveLocationData())
    val locationData: StateFlow<LiveLocationData> = _locationData.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

    private var lastRealLat: Double = -6.175392
    private var lastRealLon: Double = 106.827153
    private var lastRealAddr: String = "Jl. Raya No. 1, Jakarta, Indonesia"

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { updateLocation(it) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
                .build()

            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { updateLocation(it) }
            }
        } catch (_: Exception) {}
    }

    fun stopLocationUpdates() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
    }

    private fun updateLocation(loc: Location) {
        val now = Date()
        val formattedTime = timeFormat.format(now)
        val formattedDate = dateFormat.format(now)
        val security = AntiFakeLocationDetector.analyzeLocation(context, loc)

        scope.launch {
            val addressText = getAddressFromCoords(loc.latitude, loc.longitude)

            if (!security.isMockDetected) {
                lastRealLat = loc.latitude
                lastRealLon = loc.longitude
                lastRealAddr = addressText
            }

            _locationData.value = LiveLocationData(
                latitude = loc.latitude,
                longitude = loc.longitude,
                realLatitude = if (security.isMockDetected) lastRealLat else loc.latitude,
                realLongitude = if (security.isMockDetected) lastRealLon else loc.longitude,
                altitude = loc.altitude,
                accuracy = loc.accuracy,
                address = addressText,
                realAddress = if (security.isMockDetected) lastRealAddr else addressText,
                formattedTime = formattedTime,
                formattedDate = formattedDate,
                securityResult = security
            )
        }
    }

    private suspend fun getAddressFromCoords(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale("id", "ID"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            if (!list.isNullOrEmpty()) {
                val addr = list[0]
                val sb = StringBuilder()
                val line = addr.getAddressLine(0)
                if (!line.isNullOrEmpty()) {
                    return@withContext line
                }
                addr.subLocality?.let { sb.append(it).append(", ") }
                addr.locality?.let { sb.append(it).append(", ") }
                addr.subAdminArea?.let { sb.append(it).append(", ") }
                addr.adminArea?.let { sb.append(it).append(", ") }
                addr.countryName?.let { sb.append(it) }
                return@withContext sb.toString().trim().trimEnd(',')
            }
        } catch (_: Exception) {}
        "Jl. Raya No. 1, Jakarta, Indonesia"
    }
}
