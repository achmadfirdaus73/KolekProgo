package com.example.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.R
import com.example.location.LiveLocationData
import com.example.security.SecurityCheckResult
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class WatermarkConfig(
    val tagLabel: String = "KOLEKPRO",
    val qualityPercentage: Int = 85,
    val maxDimension: Int = 1920,
    val show3DMapBadge: Boolean = true,
    val showSecurityToken: Boolean = true
)

data class WatermarkResult(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val fileSizeBytes: Long,
    val originalSizeBytes: Long,
    val compressionRatioPercent: Int,
    val integrityHash: String
)

object WatermarkRenderer {

    fun applyWatermarkAndCompress(
        context: Context,
        sourceBitmap: Bitmap,
        locationData: LiveLocationData,
        securityResult: SecurityCheckResult,
        config: WatermarkConfig
    ): WatermarkResult {
        // 1. Scale down if exceeds maxDimension
        val scaledSource = scaleBitmap(sourceBitmap, config.maxDimension)

        // 2. Estimate uncompressed raw size
        val originalRawSize = (scaledSource.width * scaledSource.height * 4).toLong()

        // 3. Create mutable working bitmap
        val outputBitmap = scaledSource.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        val width = outputBitmap.width.toFloat()
        val height = outputBitmap.height.toFloat()

        // Dynamic scale factor based on image width (reference 1080px base)
        val scale = max(width / 1080f, 0.5f)

        // Card dimensions
        val margin = 32f * scale
        val cardWidth = width - (margin * 2)
        val cardHeight = 220f * scale
        val cardLeft = margin
        val cardBottom = height - margin
        val cardTop = cardBottom - cardHeight
        val cardRight = cardLeft + cardWidth
        val cornerRadius = 24f * scale

        // Background Card Paint (Translucent Dark Glass)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 15, 23, 42) // #0F172A dark slate
            style = Paint.Style.FILL
        }

        // Subtle Card Border Stroke
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (securityResult.isMockDetected) {
                Color.argb(180, 239, 68, 68) // Red border if fake
            } else {
                Color.argb(80, 255, 255, 255) // White subtle glass stroke
            }
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
        }

        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardBgPaint)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardBorderPaint)

        // Content padding inside card
        val pad = 24f * scale
        val contentLeft = cardLeft + pad
        val contentTop = cardTop + pad
        val contentBottom = cardBottom - pad
        val contentRight = cardRight - pad

        // --- RIGHT BADGE: 3D MAP BADGE ---
        var badgeLeft = contentRight
        val badgeSize = 150f * scale
        if (config.show3DMapBadge) {
            badgeLeft = contentRight - badgeSize
            val badgeTop = contentTop + (cardHeight - pad * 2 - badgeSize) / 2f
            draw3DMapBadge(context, canvas, badgeLeft, badgeTop, badgeSize, scale)
        }

        // --- LEFT COLUMN: Tag, Clock, Date ---
        val leftColWidth = 240f * scale

        // 1. Tag Label ("KOLEKPRO")
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8") // Bright Sky Blue
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        canvas.drawText(
            config.tagLabel.uppercase(Locale.ROOT),
            contentLeft,
            contentTop + 20f * scale,
            tagPaint
        )

        // 2. Big Clock ("18:16")
        val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val timeText = locationData.formattedTime.ifEmpty { "18:16" }
        canvas.drawText(
            timeText,
            contentLeft,
            contentTop + 84f * scale,
            clockPaint
        )

        // 3. Date ("10 Okt 2026")
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1") // Slate 300
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val dateText = locationData.formattedDate.ifEmpty { "10 Okt 2026" }
        canvas.drawText(
            dateText,
            contentLeft,
            contentTop + 124f * scale,
            datePaint
        )

        // 4. Verification Token / Mock Warning Pill
        val badgeY = contentTop + 158f * scale
        drawSecurityPill(
            canvas,
            securityResult,
            contentLeft,
            badgeY,
            scale
        )

        // Vertical Divider line between Left Column & Middle Address
        val dividerX = contentLeft + leftColWidth + 12f * scale
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            strokeWidth = 1.5f * scale
        }
        canvas.drawLine(
            dividerX,
            contentTop + 4f * scale,
            dividerX,
            contentBottom - 4f * scale,
            dividerPaint
        )

        // --- MIDDLE COLUMN: Full Address & Coordinates ---
        val midLeft = dividerX + 18f * scale
        val midRight = if (config.show3DMapBadge) badgeLeft - 16f * scale else contentRight
        val midWidth = max(midRight - midLeft, 100f)

        // 1. Multiline Address text
        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 19f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val fullAddress = locationData.address.ifEmpty {
            "RWGV+G94, Jl. Cakung Drainase, RT.1/RW.9, Semper Tim., Kec. Cilincing, Jkt Utara, DKI Jakarta 14130, Indonesia"
        }

        val addressLayout = StaticLayout.Builder.obtain(
            fullAddress,
            0,
            fullAddress.length,
            addressPaint,
            midWidth.toInt()
        )
            .setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

        canvas.save()
        canvas.translate(midLeft, contentTop + 6f * scale)
        addressLayout.draw(canvas)
        canvas.restore()

        // 2. High-Precision Coordinates ("-6,173511, 106,943335")
        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val latStr = String.format(Locale.US, "%.6f", locationData.latitude).replace('.', ',')
        val lonStr = String.format(Locale.US, "%.6f", locationData.longitude).replace('.', ',')
        val coordText = "$latStr, $lonStr"

        canvas.drawText(
            coordText,
            midLeft,
            contentBottom - 26f * scale,
            coordPaint
        )

        // 3. Mini Integrity Stamp
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 13f * scale
            typeface = Typeface.MONOSPACE
        }
        val tokenText = "SIG: #${securityResult.signatureToken} • LIVE ENCRYPTED"
        canvas.drawText(
            tokenText,
            midLeft,
            contentBottom - 6f * scale,
            stampPaint
        )

        // --- COMPRESSION ---
        val stream = ByteArrayOutputStream()
        val quality = config.qualityPercentage.coerceIn(40, 100)
        outputBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val compressedBytes = stream.toByteArray()
        val fileSizeBytes = compressedBytes.size.toLong()

        val ratio = if (originalRawSize > 0) {
            val saved = 100 - ((fileSizeBytes * 100) / originalRawSize).toInt()
            saved.coerceIn(0, 99)
        } else 0

        return WatermarkResult(
            bitmap = outputBitmap,
            jpegBytes = compressedBytes,
            fileSizeBytes = fileSizeBytes,
            originalSizeBytes = originalRawSize,
            compressionRatioPercent = ratio,
            integrityHash = securityResult.integrityHash
        )
    }

    private fun drawSecurityPill(
        canvas: Canvas,
        security: SecurityCheckResult,
        x: Float,
        y: Float,
        scale: Float
    ) {
        val isMock = security.isMockDetected
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isMock) Color.parseColor("#7F1D1D") else Color.parseColor("#064E3B")
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isMock) Color.parseColor("#FCA5A5") else Color.parseColor("#6EE7B7")
            textSize = 13f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val label = if (isMock) "⚠️ FAKE GPS" else "🛡️ ASLI TERVERIFIKASI"
        val textWidth = textPaint.measureText(label)
        val pillHeight = 22f * scale
        val pillWidth = textWidth + (16f * scale)
        val pillRect = RectF(x, y, x + pillWidth, y + pillHeight)

        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
        canvas.drawText(label, x + (8f * scale), y + (15.5f * scale), textPaint)
    }

    private fun draw3DMapBadge(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        scale: Float
    ) {
        try {
            val badgeBitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.ic_badge_3d_maps
            )
            if (badgeBitmap != null) {
                val destRect = RectF(x, y, x + size, y + size)
                // Draw white circular backing plate
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(destRect, 16f * scale, 16f * scale, bgPaint)
                canvas.drawBitmap(badgeBitmap, null, destRect, null)
                return
            }
        } catch (_: Exception) {}

        // Fallback procedural 3D map badge if asset loading fails
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        val destRect = RectF(x, y, x + size, y + size)
        canvas.drawRoundRect(destRect, 12f * scale, 12f * scale, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("3D MAPS", x + (size / 2f), y + (size / 2f) + 5f, textPaint)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (maxDim <= 0) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = (width * ratio).toInt()
        val targetHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
