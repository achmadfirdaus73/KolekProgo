package com.example.common

import com.example.data.PhotoRecord
import com.example.location.LiveLocationData
import com.example.security.SecurityCheckResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Kotlin Multiplatform (KMP) Common Interfaces
 * ---------------------------------------------
 * Abstractions implemented by platform-specific targets (androidMain and iosMain).
 */

interface PlatformLocationService {
    val locationData: StateFlow<LiveLocationData>
    fun startUpdates()
    fun stopUpdates()
}

interface PlatformPhotoStorage {
    suspend fun savePhoto(jpegBytes: ByteArray, tag: String): String
    suspend fun saveToGallery(jpegBytes: ByteArray, tag: String): Boolean
}

interface PlatformCloudSync {
    suspend fun syncRecord(record: PhotoRecord, url: String): Boolean
}

interface PlatformAntiFakeDetector {
    fun inspectLocation(latitude: Double, longitude: Double, timestampMs: Long): SecurityCheckResult
}
