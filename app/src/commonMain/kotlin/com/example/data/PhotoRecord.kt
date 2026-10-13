package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val formattedDate: String,
    val formattedTime: String,
    val tagLabel: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isMockDetected: Boolean,
    val isTimeManipulated: Boolean,
    val integrityHash: String,
    val signatureToken: String,
    val note: String = "",
    val fileSizeBytes: Long = 0,
    val cameraStyle: String = "SMART_HDR_ULTRA",
    val isSynced: Boolean = false
)
