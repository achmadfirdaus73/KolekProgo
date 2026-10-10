package com.example.security

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class SecurityCheckResult(
    val isAuthentic: Boolean,
    val isMockDetected: Boolean,
    val securityLevel: SecurityLevel,
    val warnings: List<String>,
    val integrityHash: String,
    val signatureToken: String
)

enum class SecurityLevel {
    AUTHENTIC_HIGH,
    AUTHENTIC_NORMAL,
    SUSPICIOUS,
    TAMPERED_OR_MOCK
}

object AntiFakeLocationDetector {

    private const val HMAC_SALT = "KOLEKPRO_SECURE_SALT_v2"

    fun analyzeLocation(context: Context, location: Location?): SecurityCheckResult {
        if (location == null) {
            return SecurityCheckResult(
                isAuthentic = false,
                isMockDetected = false,
                securityLevel = SecurityLevel.SUSPICIOUS,
                warnings = listOf("Sinyal GPS belum terkunci"),
                integrityHash = "UNKNOWN",
                signatureToken = "NO_GPS_LOCK"
            )
        }

        val warnings = mutableListOf<String>()
        var isMock = false

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
        } catch (_: Exception) {
            // Ignore settings read exception on newer devices where this key is restricted
        }

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

        val level = when {
            isMock -> SecurityLevel.TAMPERED_OR_MOCK
            warnings.isNotEmpty() -> SecurityLevel.SUSPICIOUS
            location.accuracy < 15.0f -> SecurityLevel.AUTHENTIC_HIGH
            else -> SecurityLevel.AUTHENTIC_NORMAL
        }

        val isAuthentic = !isMock

        // Generate cryptographic integrity hash
        val payloadToHash = "${location.latitude}:${location.longitude}:${location.time}:${location.accuracy}:${Build.FINGERPRINT}"
        val hash = sha256(payloadToHash)
        val shortToken = hmacShort("${location.time}:${hash.take(8)}")

        return SecurityCheckResult(
            isAuthentic = isAuthentic,
            isMockDetected = isMock,
            securityLevel = level,
            warnings = warnings,
            integrityHash = hash,
            signatureToken = shortToken
        )
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
