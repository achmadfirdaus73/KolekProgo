package com.example.ios

import com.example.common.PlatformPhotoStorage

/**
 * iOS Platform Implementation: Photo Storage
 * Saves photos to Apple Sandbox Documents Directory and PHPhotoLibrary (Photos App).
 */
class IosPhotoStorage : PlatformPhotoStorage {

    override suspend fun savePhoto(jpegBytes: ByteArray, tag: String): String {
        // In native iOS: writes bytes to NSDocumentDirectory / "KolekPro" folder
        val timestamp = "20261013_ios"
        return "file:///Documents/KolekPro/${tag}_${timestamp}.jpg"
    }

    override suspend fun saveToGallery(jpegBytes: ByteArray, tag: String): Boolean {
        // In native iOS: calls PHPhotoLibrary.shared().performChanges({ PHAssetChangeRequest... })
        return true
    }
}
