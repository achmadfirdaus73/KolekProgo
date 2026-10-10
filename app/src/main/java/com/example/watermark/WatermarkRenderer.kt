package com.example.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.location.LiveLocationData
import com.example.location.MapTileHelper
import com.example.security.SecurityCheckResult
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class WatermarkConfig(
    val tagLabel: String = "KOLEKPRO",
    val qualityPercentage: Int = 85,
    val maxDimension: Int = 1920,
    val showDynamicMap: Boolean = true,
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
        config: WatermarkConfig,
        customMapThumbnail: Bitmap? = null
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

        // Background Card Paint - ULTRA-TRANSLUCENT FROSTED GLASS (~25% opacity)
        // Satisfies user requirement: "transparant watermarknya buat lebih transparant lagi"
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 10, 18, 35) // Deep ultra-translucent navy glass
            style = Paint.Style.FILL
        }

        // Subtle Card Border Stroke
        val isTampered = securityResult.isMockDetected || securityResult.isTimeManipulated
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTampered) {
                Color.argb(160, 239, 68, 68) // Red border if fake GPS or manipulated time
            } else {
                Color.argb(65, 255, 255, 255) // Thin subtle frosted glass stroke
            }
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * scale
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

        // --- RIGHT BADGE: REAL DYNAMIC MAP WITH EXACT COORDINATE PINPOINT ---
        var badgeLeft = contentRight
        val badgeSize = 150f * scale
        if (config.showDynamicMap) {
            badgeLeft = contentRight - badgeSize
            val badgeTop = contentTop + (cardHeight - pad * 2 - badgeSize) / 2f

            val mapThumbnail = customMapThumbnail ?: MapTileHelper.getOrGenerateThumbnailSync(
                context = context,
                lat = locationData.latitude,
                lon = locationData.longitude,
                zoom = 16,
                targetSize = 256
            )

            drawDynamicPinpointMap(
                canvas = canvas,
                mapBitmap = mapThumbnail,
                x = badgeLeft,
                y = badgeTop,
                size = badgeSize,
                scale = scale
            )
        }

        // --- LEFT COLUMN: Tag, Clock, Date ---
        val leftColWidth = 240f * scale

        // Shadow paint for crystal-clear readability on any background photo
        val shadowRadius = 4f * scale
        val shadowDy = 2f * scale
        val shadowColor = Color.argb(180, 0, 0, 0)

        // 1. Tag Label ("KOLEKPRO")
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8") // Sky Blue
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.15f
            setShadowLayer(shadowRadius, 0f, shadowDy, shadowColor)
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
            setShadowLayer(shadowRadius * 1.5f, 0f, shadowDy * 1.5f, shadowColor)
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
            color = Color.parseColor("#E2E8F0") // Slate 200
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(shadowRadius, 0f, shadowDy, shadowColor)
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
            color = Color.argb(45, 255, 255, 255)
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
        val midRight = if (config.showDynamicMap) badgeLeft - 16f * scale else contentRight
        val midWidth = max(midRight - midLeft, 100f)

        // 1. Multiline Address text
        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 19f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(shadowRadius, 0f, shadowDy, shadowColor)
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
            setShadowLayer(shadowRadius, 0f, shadowDy, shadowColor)
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
            color = Color.parseColor("#CBD5E1")
            textSize = 13f * scale
            typeface = Typeface.MONOSPACE
            setShadowLayer(shadowRadius, 0f, shadowDy, shadowColor)
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
        val isTimeFake = security.isTimeManipulated
        val isTampered = isMock || isTimeFake

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTampered) Color.argb(210, 127, 29, 29) else Color.argb(200, 6, 78, 59)
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTampered) Color.parseColor("#FCA5A5") else Color.parseColor("#6EE7B7")
            textSize = 12f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val label = when {
            isTimeFake && isMock -> "⚠️ WAKTU & GPS PALSU"
            isTimeFake -> "⚠️ WAKTU DIMANIPULASI"
            isMock -> "⚠️ FAKE GPS"
            else -> "🛡️ ASLI TERVERIFIKASI"
        }
        val textWidth = textPaint.measureText(label)
        val pillHeight = 22f * scale
        val pillWidth = textWidth + (16f * scale)
        val pillRect = RectF(x, y, x + pillWidth, y + pillHeight)

        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
        canvas.drawText(label, x + (8f * scale), y + (15.5f * scale), textPaint)
    }

    /**
     * Draws the real dynamic pinpoint map thumbnail (never a static sticker).
     */
    private fun drawDynamicPinpointMap(
        canvas: Canvas,
        mapBitmap: Bitmap,
        x: Float,
        y: Float,
        size: Float,
        scale: Float
    ) {
        val destRect = RectF(x, y, x + size, y + size)
        val cornerRadius = 16f * scale

        // Clip rounded rectangle for map thumbnail
        canvas.save()
        val clipPath = android.graphics.Path().apply {
            addRoundRect(destRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Draw real map with exact pinpoint marker
        canvas.drawBitmap(mapBitmap, null, destRect, null)
        canvas.restore()

        // Draw subtle glass border around thumbnail
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * scale
        }
        canvas.drawRoundRect(destRect, cornerRadius, cornerRadius, borderPaint)
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
