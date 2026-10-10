package com.example.security

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class SecurityCheckResult(
    val isAuthentic: Boolean,
    val isMockDetected: Boolean,
    val isTimeManipulated: Boolean = false,
    val securityLevel: SecurityLevel,
    val warnings: List<String>,
    val integrityHash: String,
    val signatureToken: String,
    val verifiedTimeMillis: Long = System.currentTimeMillis()
)

enum class SecurityLevel {
    AUTHENTIC_HIGH,
    AUTHENTIC_NORMAL,
    SUSPICIOUS,
    TAMPERED_OR_MOCK
}

object AntiFakeLocationDetector {

    private const val HMAC_SALT = "KOLEKPRO_SECURE_SALT_v3"

    // Reference anchors to track device elapsed real-time vs wall-clock
    private var baseElapsedRealtimeNanos: Long = SystemClock.elapsedRealtimeNanos()
    private var baseSystemTimeMillis: Long = System.currentTimeMillis()

    /**
     * Analyzes GPS location authenticity AND system time integrity against clock tampering.
     */
    fun analyzeLocation(context: Context, location: Location?): SecurityCheckResult {
        val warnings = mutableListOf<String>()
        var isMock = false
        var isTimeManipulated = false

        // -------------------------------------------------------------
        // A. ANTI-MANIPULASI WAKTU (ANTI TIME TAMPERING & SPOOFING)
        // -------------------------------------------------------------
        val currentWallTime = System.currentTimeMillis()
        val currentElapsedNanos = SystemClock.elapsedRealtimeNanos()

        // 1. ElapsedRealtime drift detection (ElapsedRealtime cannot be changed by user settings)
        val elapsedDiffMillis = (currentElapsedNanos - baseElapsedRealtimeNanos) / 1_000_000L
        val expectedWallTime = baseSystemTimeMillis + elapsedDiffMillis
        val timeDriftMillis = abs(currentWallTime - expectedWallTime)

        // If user manually changed system clock backwards or forwards by more than 30 seconds
        if (timeDriftMillis > 30_000L) {
            isTimeManipulated = true
            warnings.add("Manipulasi jam sistem terdeteksi (Drift: ${timeDriftMillis / 1000} detik)")
        }

        // 2. Auto Time (Network Time) verification from device Settings
        try {
            val autoTime = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AUTO_TIME,
                1
            )
            val autoTimeZone = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AUTO_TIME_ZONE,
                1
            )
            if (autoTime == 0) {
                warnings.add("Waktu otomatis jaringan dimatikan pengguna (Manual Time)")
                // If manual time is off and significant drift occurred, mark as manipulated
                if (timeDriftMillis > 15_000L) {
                    isTimeManipulated = true
                }
            }
            if (autoTimeZone == 0) {
                warnings.add("Zona waktu otomatis dimatikan")
            }
        } catch (_: Exception) {}

        // 3. Comparison with GPS Satellite Atomic Time (if GPS location fix available)
        if (location != null && location.time > 0) {
            val gpsTime = location.time
            val timeDiffWithGps = abs(currentWallTime - gpsTime)

            // GPS satellite atomic time vs local device time
            // Tolerance: 60 seconds (accounting for network location latency, but catching manual changes by minutes/hours)
            if (timeDiffWithGps > 60_000L) {
                isTimeManipulated = true
                warnings.add("Jam perangkat berbeda dengan waktu satelit GPS (${timeDiffWithGps / 1000}s)")
            }

            // Android 7.0+ ElapsedRealtimeNanos cross-check from Location
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val locElapsedNanos = location.elapsedRealtimeNanos
                val locAgeMillis = (currentElapsedNanos - locElapsedNanos) / 1_000_000L
                if (locAgeMillis > 120_000L) {
                    warnings.add("Lokasi usang atau di-replay (${locAgeMillis / 1000}s yang lalu)")
                }
            }
        }

        // -------------------------------------------------------------
        // B. ANTI-MANIPULASI GPS (ANTI FAKE GPS / MOCK LOCATION)
        // -------------------------------------------------------------
        if (location == null) {
            warnings.add("Sinyal GPS belum terkunci")
            val isAuthentic = !isTimeManipulated
            return SecurityCheckResult(
                isAuthentic = isAuthentic,
                isMockDetected = false,
                isTimeManipulated = isTimeManipulated,
                securityLevel = if (isTimeManipulated) SecurityLevel.TAMPERED_OR_MOCK else SecurityLevel.SUSPICIOUS,
                warnings = warnings,
                integrityHash = "NO_GPS_FIX",
                signatureToken = if (isTimeManipulated) "TIME_TAMPERED" else "NO_GPS_LOCK",
                verifiedTimeMillis = currentWallTime
            )
        }

        // 1. Direct Android API Mock Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (location.isMock) {
                isMock = true
                warnings.add("Lokasi ditandai sebagai Mock oleh sistem Android 12+")
            }
        } else {
            @Suppress("DEPRECATION")
            if (location.isFromMockProvider) {
                isMock = true
                warnings.add("Lokasi ditandai dari Mock Provider")
            }
        }

        // 2. Developer Options Mock Location check
        try {
            @Suppress("DEPRECATION")
            val isMockSettingEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION,
                0
            ) != 0
            if (isMockSettingEnabled) {
                warnings.add("Pengaturan Lokasi Tiruan (Mock Location) di Developer Options aktif")
                isMock = true
            }
        } catch (_: Exception) {}

        // 3. Provider Integrity Check
        val provider = location.provider ?: ""
        if (provider.isEmpty() || provider.lowercase() == "mock") {
            isMock = true
            warnings.add("Provider lokasi tidak valid atau buatan ($provider)")
        }

        // 4. Test Provider Enumeration Check
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val testProviders = locationManager?.allProviders?.filter { it.contains("test", ignoreCase = true) } ?: emptyList()
            if (testProviders.isNotEmpty()) {
                warnings.add("Terdeteksi test provider aktif: ${testProviders.joinToString()}")
                isMock = true
            }
        } catch (_: Exception) {}

        // 5. Accuracy & Sensor Plausibility Heuristics
        if (location.accuracy <= 0.0001f && location.accuracy != 0.0f) {
            warnings.add("Akurasi GPS tidak wajar (${location.accuracy}m)")
        }

        // 6. Coordinate Range Validation
        if (location.latitude < -90.0 || location.latitude > 90.0 ||
            location.longitude < -180.0 || location.longitude > 180.0 ||
            (location.latitude == 0.0 && location.longitude == 0.0)
        ) {
            warnings.add("Koordinat tidak valid (0,0 atau di luar batas bumi)")
        }

        val isTampered = isMock || isTimeManipulated

        val level = when {
            isTampered -> SecurityLevel.TAMPERED_OR_MOCK
            warnings.isNotEmpty() -> SecurityLevel.SUSPICIOUS
            location.accuracy < 15.0f -> SecurityLevel.AUTHENTIC_HIGH
            else -> SecurityLevel.AUTHENTIC_NORMAL
        }

        val isAuthentic = !isTampered

        // Cryptographic integrity hash containing coordinate + hardware + atomic time
        val payloadToHash = "${location.latitude}:${location.longitude}:${location.time}:${currentElapsedNanos}:${Build.FINGERPRINT}:timeTampered=$isTimeManipulated"
        val hash = sha256(payloadToHash)
        val shortToken = hmacShort("${location.time}:${hash.take(8)}")

        return SecurityCheckResult(
            isAuthentic = isAuthentic,
            isMockDetected = isMock,
            isTimeManipulated = isTimeManipulated,
            securityLevel = level,
            warnings = warnings,
            integrityHash = hash,
            signatureToken = shortToken,
            verifiedTimeMillis = currentWallTime
        )
    }

    /**
     * Resets the internal time anchor, e.g. when app cold-starts or network time is synced.
     */
    fun recalibrateTimeAnchor() {
        baseElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        baseSystemTimeMillis = System.currentTimeMillis()
    }

    private fun sha256(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            "HASH_ERROR"
        }
    }

    private fun hmacShort(data: String): String {
        return try {
            val keySpec = SecretKeySpec(HMAC_SALT.toByteArray(), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)
            val bytes = mac.doFinal(data.toByteArray())
            bytes.take(4).joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            "AUTH-OK"
        }
    }
}
