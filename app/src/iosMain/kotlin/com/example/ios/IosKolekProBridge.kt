package com.example.ios

import com.example.config.AppConfig
import com.example.location.LiveLocationData
import com.example.security.SecurityCheckResult

/**
 * iOS Bridge Entry Point
 * Exports shared KMP logic and config to iOS Swift / SwiftUI layer.
 */
object IosKolekProBridge {

    val defaultTag: String = AppConfig.DEFAULT_COMPANY_TAG
    val defaultUrl: String = AppConfig.SPREADSHEET_WEBAPP_URL

    fun formatCoordinates(lat: Double, lon: Double): String {
        return String.format("%.6f, %.6f", lat, lon)
    }

    fun verifyIntegrityToken(token: String): Boolean {
        return token.isNotEmpty() && token.length >= 8
    }
}
