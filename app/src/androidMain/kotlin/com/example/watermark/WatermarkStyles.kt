package com.example.watermark

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.location.LiveLocationData
import com.example.location.MapTileHelper
import com.example.security.SecurityCheckResult
import java.util.Locale
import kotlin.math.max

object WatermarkStyles {

    fun drawModernDarkBottomLeft(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#0F172A"))
        drawMapThumbnail(canvas, cardRect, cornerRadius, scale, customMapThumbnail, locationData, context, isRight = false)
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale)
    }
    fun drawModernDarkBottomRight(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#0F172A"))
        drawMapThumbnail(canvas, cardRect, cornerRadius, scale, customMapThumbnail, locationData, context, isRight = true)
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale)
    }
    fun drawModernLightBottomRight(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#FFFFFF"))
        drawMapThumbnail(canvas, cardRect, cornerRadius, scale, customMapThumbnail, locationData, context, isRight = true)
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale, textColor = Color.BLACK)
    }
    fun drawModernLightBottomLeft(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#FFFFFF"))
        drawMapThumbnail(canvas, cardRect, cornerRadius, scale, customMapThumbnail, locationData, context, isRight = false)
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale, textColor = Color.BLACK)
    }
    fun drawMinimalDarkTopLeft(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#0F172A"))
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale)
    }
    fun drawModernLightBottomCenter(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#FFFFFF"))
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale, textColor = Color.BLACK)
    }
    fun drawVerticalDarkLeft(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#0F172A"))
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale)
    }
    fun drawRoundedDarkBottomLeft(canvas: Canvas, context: Context, locationData: LiveLocationData, securityResult: SecurityCheckResult, config: WatermarkConfig, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?) {
        drawBackground(canvas, cardRect, cornerRadius, Color.parseColor("#0F172A"))
        drawStandardContent(canvas, locationData, securityResult, config, cardRect, scale)
    }

    private fun drawMapThumbnail(canvas: Canvas, cardRect: RectF, cornerRadius: Float, scale: Float, customMapThumbnail: Bitmap?, locationData: LiveLocationData, context: Context, isRight: Boolean) {
        val mapSize = 100f * scale
        val mapRect = if (isRight) RectF(cardRect.right - mapSize - 10f * scale, cardRect.top + 10f * scale, cardRect.right - 10f * scale, cardRect.top + mapSize + 10f * scale)
                      else RectF(cardRect.left + 10f * scale, cardRect.top + 10f * scale, cardRect.left + mapSize + 10f * scale, cardRect.top + mapSize + 10f * scale)
        val mapTile = customMapThumbnail ?: MapTileHelper.getRawTileSync(context, locationData.latitude, locationData.longitude, 16)
        canvas.drawBitmap(mapTile, null, mapRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawBackground(canvas: Canvas, cardRect: RectF, cornerRadius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)
    }

    fun drawStandardContent(
        canvas: Canvas, locationData: LiveLocationData, securityResult: SecurityCheckResult,
        config: WatermarkConfig, cardRect: RectF, scale: Float, textColor: Int = Color.WHITE
    ) {
        val pad = 24f * scale
        val contentLeft = cardRect.left + pad
        val contentTop = cardRect.top + pad

        val shadowRadius = 4f * scale
        val shadowDy = 2f * scale
        val shadowColor = if (textColor == Color.WHITE) Color.argb(180, 0, 0, 0) else Color.TRANSPARENT

        // 1. Tag Label
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (textColor == Color.WHITE) Color.parseColor("#38BDF8") else Color.parseColor("#1E3A8A")
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.15f
            if (textColor == Color.WHITE) setShadowLayer(shadowRadius, 0f, shadowDy, shadowColor)
        }
        canvas.drawText(config.tagLabel.uppercase(Locale.ROOT), contentLeft, contentTop + 20f * scale, tagPaint)

        // 2. Big Clock
        val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 58f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            if (textColor == Color.WHITE) setShadowLayer(shadowRadius * 1.5f, 0f, shadowDy * 1.5f, shadowColor)
        }
        val timeText = locationData.formattedTime.ifEmpty { "18:16" }
        canvas.drawText(timeText, contentLeft, contentTop + 84f * scale, clockPaint)

        // 3. Date
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (textColor == Color.WHITE) Color.parseColor("#E2E8F0") else Color.parseColor("#475569")
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (textColor == Color.WHITE) setShadowLayer(shadowRadius, 0f, shadowDy, shadowColor)
        }
        val dateText = locationData.formattedDate.ifEmpty { "10 Okt 2026" }
        canvas.drawText(dateText, contentLeft, contentTop + 124f * scale, datePaint)
        
        // 4. Status Pill
        drawSecurityPill(canvas, securityResult, contentLeft, contentTop + 158f * scale, scale)
    }

    fun drawSecurityPill(canvas: Canvas, security: SecurityCheckResult, x: Float, y: Float, scale: Float) {
        val isTampered = security.isMockDetected || security.isTimeManipulated
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
            security.isTimeManipulated && security.isMockDetected -> "⚠️ WAKTU & GPS PALSU"
            security.isTimeManipulated -> "⚠️ WAKTU DIMANIPULASI"
            security.isMockDetected -> "⚠️ FAKE GPS"
            else -> "🛡️ ASLI TERVERIFIKASI"
        }
        val textWidth = textPaint.measureText(label)
        val pillHeight = 22f * scale
        val pillWidth = textWidth + (16f * scale)
        val pillRect = RectF(x, y, x + pillWidth, y + pillHeight)

        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
        canvas.drawText(label, x + (8f * scale), y + (15.5f * scale), textPaint)
    }

}
