package com.example.security

data class SecurityCheckResult(
    val isAuthentic: Boolean,
    val isMockDetected: Boolean,
    val isTimeManipulated: Boolean,
    val integrityHash: String,
    val signatureToken: String,
    val detectedAnomalies: List<String> = emptyList()
)
