package com.example.security

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object AntiFakeLocationDetector {

    fun analyzeLocation(context: Context, location: Location?): SecurityCheckResult {
        val anomalies = mutableListOf<String>()
        var isMock = false

        if (location != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (location.isMock) {
                    isMock = true
                    anomalies.add("Mock location detected via Android 12+ API")
                }
            } else {
                @Suppress("DEPRECATION")
                if (location.isFromMockProvider) {
                    isMock = true
                    anomalies.add("Mock location provider detected")
                }
            }
        }

        // Time manipulation detection
        val systemTime = System.currentTimeMillis()
        val elapsedRealtime = SystemClock.elapsedRealtime()
        var isTimeFake = false

        if (location != null && location.time > 0) {
            val diff = abs(systemTime - location.time)
            // If system clock differs from GPS clock by more than 15 minutes
            if (diff > 15 * 60 * 1000) {
                isTimeFake = true
                anomalies.add("System clock differs significantly from GPS timestamp")
            }
        }

        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val rawSignature = "$lat|$lon|$systemTime|$elapsedRealtime|$isMock"
        val hash = sha256(rawSignature)
        val token = hash.take(8).uppercase(Locale.ROOT)

        return SecurityCheckResult(
            isAuthentic = !isMock && !isTimeFake,
            isMockDetected = isMock,
            isTimeManipulated = isTimeFake,
            integrityHash = hash,
            signatureToken = token,
            detectedAnomalies = anomalies
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
