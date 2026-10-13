package com.example.ios

import com.example.location.LiveLocationData
import com.example.security.SecurityCheckResult

/**
 * iOS Platform Implementation: Watermark Renderer
 * Wraps Apple CoreGraphics (UIGraphicsImageRenderer) for drawing watermarks on iOS.
 */
object IosWatermarkRenderer {

    data class IosWatermarkConfig(
        val companyTag: String = "KOLEKPRO",
        val watermarkAlpha: Float = 0.24f
    )

    fun stampWatermarkOnBytes(
        jpegBytes: ByteArray,
        locationData: LiveLocationData,
        securityResult: SecurityCheckResult,
        config: IosWatermarkConfig
    ): ByteArray {
        // In native iOS: loads UIImage from data, renders overlay with UIGraphicsImageRenderer,
        // returns UIImageJPEGRepresentation with high compression quality.
        return jpegBytes
    }
}
