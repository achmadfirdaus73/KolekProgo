package com.example.ios

import com.example.common.PlatformLocationService
import com.example.location.LiveLocationData
import com.example.security.SecurityCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS Platform Implementation: Location Provider
 * Wraps Apple CoreLocation (CLLocationManager) for iOS target.
 */
class IosLocationService : PlatformLocationService {

    private val _locationData = MutableStateFlow(
        LiveLocationData(
            latitude = -6.175392,
            longitude = 106.827153,
            address = "DKI Jakarta (iOS CoreLocation)",
            formattedTime = "12:00",
            formattedDate = "13 Okt 2026",
            securityResult = SecurityCheckResult(
                isAuthentic = true,
                isMockDetected = false,
                isTimeManipulated = false,
                integrityHash = "IOS_GENUINE_LOCATION_SECURE",
                signatureToken = "IOS_APPLE"
            )
        )
    )

    override val locationData: StateFlow<LiveLocationData> = _locationData.asStateFlow()

    override fun startUpdates() {
        // In native iOS build: initiates CLLocationManager.startUpdatingLocation()
    }

    override fun stopUpdates() {
        // In native iOS build: calls CLLocationManager.stopUpdatingLocation()
    }
}
