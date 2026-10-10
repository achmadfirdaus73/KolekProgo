package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_records")
data class PhotoRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val timestamp: Long,
    val formattedDate: String,
    val formattedTime: String,
    val tagLabel: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val isMockDetected: Boolean,
    val integrityHash: String,
    val signatureToken: String,
    val note: String = "",
    val fileSizeBytes: Long = 0,
    val syncStatus: String = SYNC_STATUS_PENDING,
    val syncTimestamp: Long? = null,
    val syncErrorMessage: String? = null
) {
    companion object {
        const val SYNC_STATUS_PENDING = "PENDING"
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_FAILED = "FAILED"
    }
}
