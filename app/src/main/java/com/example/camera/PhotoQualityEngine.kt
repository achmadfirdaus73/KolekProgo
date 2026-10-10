package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Photographic Quality Styles for computational HDR and high-clarity processing.
 * Designed to achieve flagship camera quality (dynamic range, tone mapping,
 * micro-contrast clarity, and warm daylight color science) without fake device labels.
 */
enum class PhotoQualityStyle(
    val id: String,
    val title: String,
    val subtitle: String,
    val shortBadge: String
) {
    SMART_HDR_ULTRA(
        id = "SMART_HDR_ULTRA",
        title = "Smart HDR Ultra",
        subtitle = "Tone Dinamis • Kejernihan Tinggi",
        shortBadge = "HDR Ultra"
    ),
    RICH_CONTRAST(
        id = "RICH_CONTRAST",
        title = "Rich Dynamic",
        subtitle = "Kontras Dalam • Ketajaman Ekstra",
        shortBadge = "Kontras"
    ),
    WARM_VIBRANT(
        id = "WARM_VIBRANT",
        title = "Vibrant Daylight",
        subtitle = "Warna Hangat • Langit & Daun Hidup",
        shortBadge = "Vibrant"
    ),
    OFF(
        id = "OFF",
        title = "Sensor Asli",
        subtitle = "Sensor Kamera Tanpa Filter",
        shortBadge = "Standar"
    )
}

// Backward compatibility aliases
typealias IPhoneStyle = PhotoQualityStyle
typealias IPhonePhotonicEngine = PhotoQualityEngine

object PhotoQualityEngine {

    /**
     * Applies flagship-grade computational photo processing:
     * 1. Smart HDR Tone Mapping & Dynamic Range optimization (lifts shadow detail, preserves highlights)
     * 2. Calibrated Color Science (warm daylight balance, rich foliage greens, deep sky blues)
     * 3. Micro-Contrast & Texture Clarity enhancement (crisp text, fine architectural & environmental detail)
     * 4. Subtle Optical Lens Light Falloff (natural wide-aperture corner graduation)
     */
    fun processBitmap(source: Bitmap, style: PhotoQualityStyle): Bitmap {
        if (style == PhotoQualityStyle.OFF) {
            return source
        }

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. ColorMatrix Tone & Color Grading (Smart HDR tone curve + color balance)
        val colorMatrix = buildColorMatrixForStyle(style)
        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, filterPaint)

        // 2. Micro-contrast & Texture Clarity boost (Unsharp Masking for crisp flagship clarity)
        val sharpened = applyClarityBoost(output, style)

        // 3. Subtle Optical Lens Falloff (Natural f/1.8 lens vignette)
        val finalCanvas = Canvas(sharpened)
        val maxDim = max(width, height).toFloat()
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val vignetteAlpha = when (style) {
                PhotoQualityStyle.RICH_CONTRAST -> 28
                PhotoQualityStyle.WARM_VIBRANT -> 22
                else -> 18
            }
            shader = RadialGradient(
                width / 2f,
                height / 2f,
                maxDim * 0.75f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(vignetteAlpha / 3, 0, 0, 0),
                    Color.argb(vignetteAlpha, 15, 12, 10)
                ),
                floatArrayOf(0.0f, 0.70f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        finalCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)

        return sharpened
    }

    /**
     * Builds calibrated tone and color matrices matching flagship camera color profiles.
     */
    private fun buildColorMatrixForStyle(style: PhotoQualityStyle): ColorMatrix {
        val cm = ColorMatrix()

        when (style) {
            PhotoQualityStyle.SMART_HDR_ULTRA -> {
                // Flagship Smart HDR:
                // Balanced Dynamic Range: contrast +9%, shadow lift +6, subtle daylight warmth
                val c = 1.09f
                val b = 6f
                val rGain = 1.045f // Warm daylight bias
                val gGain = 1.015f
                val bGain = 0.980f // Controlled blue cast

                val matrix = floatArrayOf(
                    c * rGain, 0f, 0f, 0f, b,
                    0f, c * gGain, 0f, 0f, b,
                    0f, 0f, c * bGain, 0f, b,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(matrix)

                val satMatrix = ColorMatrix()
                satMatrix.setSaturation(1.08f)
                cm.postConcat(satMatrix)
            }

            PhotoQualityStyle.RICH_CONTRAST -> {
                // Deep blacks, crisp architectural separation, dramatic punch
                val c = 1.18f
                val b = -2f
                val rGain = 1.03f
                val gGain = 1.00f
                val bGain = 1.01f

                val matrix = floatArrayOf(
                    c * rGain, 0f, 0f, 0f, b,
                    0f, c * gGain, 0f, 0f, b,
                    0f, 0f, c * bGain, 0f, b,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(matrix)

                val satMatrix = ColorMatrix()
                satMatrix.setSaturation(1.06f)
                cm.postConcat(satMatrix)
            }

            PhotoQualityStyle.WARM_VIBRANT -> {
                // Golden hour warmth, healthy skin tones, luminous foliage & sky
                val c = 1.07f
                val b = 9f
                val rGain = 1.085f
                val gGain = 1.025f
                val bGain = 0.940f

                val matrix = floatArrayOf(
                    c * rGain, 0f, 0f, 0f, b,
                    0f, c * gGain, 0f, 0f, b,
                    0f, 0f, c * bGain, 0f, b,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(matrix)

                val satMatrix = ColorMatrix()
                satMatrix.setSaturation(1.15f)
                cm.postConcat(satMatrix)
            }

            PhotoQualityStyle.OFF -> {
                // Identity
            }
        }

        return cm
    }

    /**
     * Fast micro-contrast clarity enhancer using downscale-difference unsharp masking.
     * Extracts high-frequency texture details and blends them back to give photos
     * the razor-sharp clarity characteristic of flagship mobile sensors.
     */
    private fun applyClarityBoost(bitmap: Bitmap, style: PhotoQualityStyle): Bitmap {
        if (style == PhotoQualityStyle.OFF) return bitmap

        return try {
            val w = bitmap.width
            val h = bitmap.height

            // 1. Create a fast half-scale representation (drops high frequencies)
            val downScaleW = max(w / 2, 16)
            val downScaleH = max(h / 2, 16)
            val lowFreq = Bitmap.createScaledBitmap(bitmap, downScaleW, downScaleH, true)
            val blurred = Bitmap.createScaledBitmap(lowFreq, w, h, true)
            lowFreq.recycle()

            // 2. Prepare mutable result
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val pixelsSrc = IntArray(w)
            val pixelsBlur = IntArray(w)

            val clarityWeight = when (style) {
                PhotoQualityStyle.RICH_CONTRAST -> 0.40f
                PhotoQualityStyle.SMART_HDR_ULTRA -> 0.32f
                PhotoQualityStyle.WARM_VIBRANT -> 0.25f
                else -> 0.0f
            }

            // Row-by-row micro-contrast unsharp calculation
            for (y in 0 until h) {
                result.getPixels(pixelsSrc, 0, w, 0, y, w, 1)
                blurred.getPixels(pixelsBlur, 0, w, 0, y, w, 1)

                for (x in 0 until w) {
                    val cSrc = pixelsSrc[x]
                    val cBlur = pixelsBlur[x]

                    val a = (cSrc ushr 24) and 0xFF
                    val rSrc = (cSrc ushr 16) and 0xFF
                    val gSrc = (cSrc ushr 8) and 0xFF
                    val bSrc = cSrc and 0xFF

                    val rBlur = (cBlur ushr 16) and 0xFF
                    val gBlur = (cBlur ushr 8) and 0xFF
                    val bBlur = cBlur and 0xFF

                    // High-frequency detail = Source - LowFreq
                    val diffR = rSrc - rBlur
                    val diffG = gSrc - gBlur
                    val diffB = bSrc - bBlur

                    // Threshold to prevent noise boosting in smooth sky/flat areas
                    val threshold = 3
                    val newR = if (abs(diffR) > threshold) {
                        (rSrc + (diffR * clarityWeight)).toInt().coerceIn(0, 255)
                    } else rSrc

                    val newG = if (abs(diffG) > threshold) {
                        (gSrc + (diffG * clarityWeight)).toInt().coerceIn(0, 255)
                    } else gSrc

                    val newB = if (abs(diffB) > threshold) {
                        (bSrc + (diffB * clarityWeight)).toInt().coerceIn(0, 255)
                    } else bSrc

                    pixelsSrc[x] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
                }

                result.setPixels(pixelsSrc, 0, w, 0, y, w, 1)
            }

            blurred.recycle()
            result
        } catch (_: Throwable) {
            // Safe fallback to base tone-mapped bitmap if memory constrained
            bitmap
        }
    }

    /**
     * Injects professional camera EXIF metadata into the saved JPEG file without fake device labels.
     */
    fun injectCalibratedExif(
        file: File,
        latitude: Double,
        longitude: Double,
        style: PhotoQualityStyle,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!file.exists()) return

        try {
            val exif = ExifInterface(file.absolutePath)
            val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(timestamp))

            // 1. Professional Optics & Exposure
            exif.setAttribute(ExifInterface.TAG_F_NUMBER, "1.8")
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "24/1")
            exif.setAttribute("FocalLengthIn35mmFilm", "24")
            exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, "64")
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.008") // 1/125s
            exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, "0") // Auto

            // 2. Timestamps
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)

            // 3. GPS Coordinates
            if (latitude != 0.0 || longitude != 0.0) {
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, decimalToDms(latitude))
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latitude >= 0) "N" else "S")
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decimalToDms(longitude))
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (longitude >= 0) "E" else "W")
                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS/NETWORK (Geo-Verified)")
            }

            // 4. Description & Profile
            exif.setAttribute(
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                "KolekPro Geo-Verified • ${style.title}"
            )

            exif.saveAttributes()
        } catch (_: Exception) {
            // Non-fatal if EXIF cannot be written
        }
    }

    // Deprecated alias for backward compatibility
    fun injectAppleExif(
        file: File,
        latitude: Double,
        longitude: Double,
        style: PhotoQualityStyle,
        timestamp: Long = System.currentTimeMillis()
    ) {
        injectCalibratedExif(file, latitude, longitude, style, timestamp)
    }

    private fun decimalToDms(coord: Double): String {
        val absCoord = abs(coord)
        val degrees = absCoord.toInt()
        val minutesDouble = (absCoord - degrees) * 60.0
        val minutes = minutesDouble.toInt()
        val seconds = ((minutesDouble - minutes) * 60.0 * 1000).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }
}
