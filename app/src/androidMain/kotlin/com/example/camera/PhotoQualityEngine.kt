package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

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

typealias IphoneStyle = PhotoQualityStyle
typealias IPhoneStyle = PhotoQualityStyle
typealias IPhonePhotonicEngine = PhotoQualityEngine

object PhotoQualityEngine {

    fun processBitmap(source: Bitmap, style: PhotoQualityStyle): Bitmap {
        if (style == PhotoQualityStyle.OFF) {
            return source
        }

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cm = ColorMatrix()
        when (style) {
            PhotoQualityStyle.SMART_HDR_ULTRA -> {
                cm.set(floatArrayOf(
                    1.10f, 0f, 0f, 0f, 4f,
                    0f, 1.08f, 0f, 0f, 4f,
                    0f, 0f, 1.05f, 0f, 4f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            PhotoQualityStyle.RICH_CONTRAST -> {
                cm.set(floatArrayOf(
                    1.20f, 0f, 0f, 0f, -6f,
                    0f, 1.20f, 0f, 0f, -6f,
                    0f, 0f, 1.20f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            PhotoQualityStyle.WARM_VIBRANT -> {
                cm.set(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 8f,
                    0f, 1.10f, 0f, 0f, 4f,
                    0f, 0f, 0.95f, 0f, -2f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            else -> {}
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
