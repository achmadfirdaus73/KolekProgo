package com.example.security

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object AntiFakeLocationDetector {

    private var persistentMockDetected = false
    private var persistentTimeFakeDetected = false

    fun analyzeLocation(context: Context, location: Location?): SecurityCheckResult {
        val anomalies = mutableListOf<String>()
        var isMock = persistentMockDetected

        if (location != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (location.isMock) {
                    isMock = true
                    persistentMockDetected = true
                    anomalies.add("Mock location detected via Android 12+ API")
                }
            } else {
                @Suppress("DEPRECATION")
                if (location.isFromMockProvider) {
                    isMock = true
                    persistentMockDetected = true
                    anomalies.add("Mock location provider detected")
                }
            }
        }

        if (persistentMockDetected && !anomalies.any { it.contains("Mock", ignoreCase = true) }) {
            anomalies.add("Mock location previously detected (locked)")
        }

        // Time manipulation detection with strict persistence and manual time check
        val systemTime = System.currentTimeMillis()
        val elapsedRealtime = SystemClock.elapsedRealtime()
        var isTimeFake = persistentTimeFakeDetected

        // Check if automatic time is disabled in device settings (manual clock tampering)
        try {
            val autoTime = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 1)
            val autoTimeZone = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 1)
            if (autoTime == 0 || autoTimeZone == 0) {
                isTimeFake = true
                persistentTimeFakeDetected = true
                anomalies.add("Device automatic date/time or time zone is disabled (manual time override)")
            }
        } catch (_: Exception) {}

        if (location != null && location.time > 0) {
            val diff = abs(systemTime - location.time)
            // If system clock differs from GPS clock by more than 10 minutes
            if (diff > 10 * 60 * 1000) {
                isTimeFake = true
                persistentTimeFakeDetected = true
                anomalies.add("System clock differs significantly from GPS timestamp")
            }
        }

        if (persistentTimeFakeDetected && !anomalies.any { it.contains("time", ignoreCase = true) || it.contains("clock", ignoreCase = true) }) {
            anomalies.add("Time manipulation detected and locked for session")
        }

        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val rawSignature = "$lat|$lon|$systemTime|$elapsedRealtime|$isMock|$isTimeFake"
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
