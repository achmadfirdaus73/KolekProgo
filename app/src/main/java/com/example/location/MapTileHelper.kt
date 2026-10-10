package com.example.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

object MapTileHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var memoryCachedThumbnail: Bitmap? = null
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    /**
     * Renders a real-time dynamic map thumbnail showing the exact point marker
     * at the provided latitude and longitude.
     */
    suspend fun renderLocationMapThumbnail(
        context: Context,
        lat: Double,
        lon: Double,
        zoom: Int = 16,
        targetSize: Int = 256
    ): Bitmap = withContext(Dispatchers.IO) {
        // Return memory cached thumbnail if user hasn't moved significantly (~10 meters)
        if (memoryCachedThumbnail != null &&
            kotlin.math.abs(lat - lastLat) < 0.0001 &&
            kotlin.math.abs(lon - lastLon) < 0.0001
        ) {
            return@withContext memoryCachedThumbnail!!
        }

        // 1. Fetch raw map tile
        val rawTile = fetchRawMapTile(context, lat, lon, zoom)

        // 2. Render composite thumbnail with real pinpoint marker and radar rings
        val composite = createPinpointedMapThumbnail(rawTile, lat, lon, zoom, targetSize)

        lastLat = lat
        lastLon = lon
        memoryCachedThumbnail = composite
        composite
    }

    /**
     * Alias for LocationTracker compatibility.
     */
    suspend fun fetchLocationMapTile(
        context: Context,
        lat: Double,
        lon: Double,
        zoom: Int = 16
    ): Bitmap = renderLocationMapThumbnail(context, lat, lon, zoom)

    /**
     * Synchronous fallback to ensure the watermark renderer always has a valid pinpointed map
     * even if network is unavailable or called from non-coroutine contexts.
     */
    fun getOrGenerateThumbnailSync(
        context: Context,
        lat: Double,
        lon: Double,
        zoom: Int = 16,
        targetSize: Int = 256
    ): Bitmap {
        if (memoryCachedThumbnail != null &&
            kotlin.math.abs(lat - lastLat) < 0.0001 &&
            kotlin.math.abs(lon - lastLon) < 0.0001
        ) {
            return memoryCachedThumbnail!!
        }

        val diskTile = getCachedDiskTile(context, lat, lon, zoom)
        val baseTile = diskTile ?: createProceduralMap(lat, lon)
        val composite = createPinpointedMapThumbnail(baseTile, lat, lon, zoom, targetSize)
        lastLat = lat
        lastLon = lon
        memoryCachedThumbnail = composite
        return composite
    }

    private fun fetchRawMapTile(
        context: Context,
        lat: Double,
        lon: Double,
        zoom: Int
    ): Bitmap {
        if (lat == 0.0 && lon == 0.0) {
            return createProceduralMap(lat, lon)
        }

        // Try disk cache first
        val diskTile = getCachedDiskTile(context, lat, lon, zoom)
        if (diskTile != null) {
            return diskTile
        }

        val tileX = lonToTileX(lon, zoom)
        val tileY = latToTileY(lat, zoom)

        val cacheDir = File(context.cacheDir, "map_tiles")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "tile_${zoom}_${tileX}_${tileY}.png")

        val urls = listOf(
            "https://basemaps.cartocdn.com/rastertiles/voyager/$zoom/$tileX/$tileY.png",
            "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
        )

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "KolekProCam-Android/1.0 (Location Watermark)")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val downloadedBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (downloadedBmp != null) {
                            try {
                                FileOutputStream(cacheFile).use { out ->
                                    out.write(bytes)
                                    out.flush()
                                }
                            } catch (_: Exception) {}
                            return downloadedBmp
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return createProceduralMap(lat, lon)
    }

    private fun getCachedDiskTile(context: Context, lat: Double, lon: Double, zoom: Int): Bitmap? {
        val tileX = lonToTileX(lon, zoom)
        val tileY = latToTileY(lat, zoom)
        val cacheDir = File(context.cacheDir, "map_tiles")
        val cacheFile = File(cacheDir, "tile_${zoom}_${tileX}_${tileY}.png")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return try {
                BitmapFactory.decodeFile(cacheFile.absolutePath)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Composites the base map tile with a high-accuracy GPS pinpoint marker,
     * radar pulse circles, and live map badge.
     */
    private fun createPinpointedMapThumbnail(
        baseTile: Bitmap,
        lat: Double,
        lon: Double,
        zoom: Int,
        targetSize: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Calculate the exact coordinate offset inside the tile
        val n = 1 shl zoom
        val tileXFloat = ((lon + 180.0) / 360.0 * n)
        val latRad = Math.toRadians(lat)
        val tileYFloat = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n)
        val tileX = floor(tileXFloat).toInt()
        val tileY = floor(tileYFloat).toInt()

        val subX = (tileXFloat - tileX).toFloat().coerceIn(0.1f, 0.9f)
        val subY = (tileYFloat - tileY).toFloat().coerceIn(0.1f, 0.9f)

        // Crop / scale base tile into canvas
        val srcRect = Rect(0, 0, baseTile.width, baseTile.height)
        val destRect = Rect(0, 0, targetSize, targetSize)
        canvas.drawBitmap(baseTile, srcRect, destRect, null)

        // Target pinpoint center point in targetSize coordinates
        val pinX = (subX * targetSize)
        val pinY = (subY * targetSize)

        // 2. Draw radar pulse / accuracy ring around the exact point
        val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B82F6")
            alpha = 50
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pinX, pinY, 32f, radarPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            alpha = 140
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(pinX, pinY, 32f, ringPaint)

        // 3. Draw location pin shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(RectF(pinX - 10f, pinY + 1f, pinX + 10f, pinY + 7f), shadowPaint)

        // 4. Draw high-visibility 3D Location Marker Pin
        val pinPath = Path()
        val pinTop = pinY - 30f
        val pinRadius = 13f

        // Pin body
        pinPath.moveTo(pinX, pinY)
        pinPath.cubicTo(
            pinX - 16f, pinY - 14f,
            pinX - pinRadius, pinTop + pinRadius,
            pinX - pinRadius, pinTop
        )
        pinPath.arcTo(
            RectF(pinX - pinRadius, pinTop - pinRadius, pinX + pinRadius, pinTop + pinRadius),
            180f,
            180f
        )
        pinPath.cubicTo(
            pinX + pinRadius, pinTop + pinRadius,
            pinX + 16f, pinY - 14f,
            pinX, pinY
        )
        pinPath.close()

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF4444") // Vibrant Red
            style = Paint.Style.FILL
        }
        val pinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        canvas.drawPath(pinPath, pinPaint)
        canvas.drawPath(pinPath, pinStrokePaint)

        // Inner white dot in the pin head
        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pinX, pinTop, 4.5f, innerDotPaint)

        // 5. Draw bottom badge: "LIVE GPS"
        val badgeHeight = 22f
        val badgeWidth = 92f
        val badgeLeft = (targetSize - badgeWidth) / 2f
        val badgeTop = targetSize - badgeHeight - 6f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 15, 23, 42) // Dark glass slate
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(badgeRect, 11f, 11f, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, 11f, 11f, badgeBorderPaint)

        // Live beacon dot in badge
        val liveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981") // Emerald Green
            style = Paint.Style.FILL
        }
        canvas.drawCircle(badgeLeft + 12f, badgeTop + 11f, 3.5f, liveDotPaint)

        // Text "GPS LIVE"
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("GPS LIVE", badgeLeft + 22f, badgeTop + 14.5f, badgeTextPaint)

        return output
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        return floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return floor((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
    }

    private fun createProceduralMap(lat: Double, lon: Double): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Light map terrain background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Green zones / parks
        val parkPaint = Paint().apply {
            color = Color.parseColor("#D1FAE5")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(16f, 20f, 100f, 90f), 12f, 12f, parkPaint)
        canvas.drawRoundRect(RectF(160f, 150f, 240f, 220f), 12f, 12f, parkPaint)

        // Water feature / river
        val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BAE6FD")
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawLine(0f, 180f, size.toFloat(), 130f, waterPaint)

        // Street grid lines
        val mainRoadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val roadOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }

        val roads = listOf(
            Pair(0f to 128f, size.toFloat() to 128f),
            Pair(128f to 0f, 128f to size.toFloat()),
            Pair(40f to 0f, 40f to size.toFloat()),
            Pair(216f to 0f, 216f to size.toFloat()),
            Pair(0f to 60f, size.toFloat() to 60f),
            Pair(0f to 200f, size.toFloat() to 200f)
        )

        for ((start, end) in roads) {
            canvas.drawLine(start.first, start.second, end.first, end.second, roadOutlinePaint)
            canvas.drawLine(start.first, start.second, end.first, end.second, mainRoadPaint)
        }

        return bitmap
    }
}
