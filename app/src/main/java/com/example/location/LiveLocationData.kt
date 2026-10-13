package com.example.location

import com.example.security.SecurityCheckResult

data class LiveLocationData(
    val latitude: Double = -6.175392,
    val longitude: Double = 106.827153,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val address: String = "",
    val formattedTime: String = "",
    val formattedDate: String = "",
    val securityResult: SecurityCheckResult = SecurityCheckResult(
        isAuthentic = true,
        isMockDetected = false,
        isTimeManipulated = false,
        integrityHash = "GENUINE_STAMP_DEFAULT",
        signatureToken = "D4A77499"
    )
)
