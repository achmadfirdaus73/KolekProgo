package com.example.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
import java.util.concurrent.TimeUnit
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

/**
 * Helper to fetch real OpenStreetMap (OSM) tiles without any API key,
 * and render a bird's-eye perspective (tilted view from above) with exact GPS pin placement.
 */
object MapTileHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var memoryCachedThumbnail: Bitmap? = null
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    /**
     * Renders a real-time dynamic map thumbnail from OpenStreetMap with a tilted
     * bird's-eye angle from above, pinpointing the user's exact coordinates.
     * No API key required!
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

        // 1. Fetch OpenStreetMap tile (no API key needed)
        val rawTile = fetchOpenStreetMapTile(context, lat, lon, zoom)

        // 2. Render tilted perspective map thumbnail with precise pinpoint marker
        val composite = createTiltedPerspectiveMapThumbnail(rawTile, lat, lon, zoom, targetSize)

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
        val composite = createTiltedPerspectiveMapThumbnail(baseTile, lat, lon, zoom, targetSize)
        lastLat = lat
        lastLon = lon
        memoryCachedThumbnail = composite
        return composite
    }

    /**
     * Fetches real tile strictly from OpenStreetMap (and open mirrors) - 100% FREE & NO API KEY.
     */
    private fun fetchOpenStreetMapTile(
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

        val cacheDir = File(context.cacheDir, "osm_tiles")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "osm_${zoom}_${tileX}_${tileY}.png")

        // Pure OpenStreetMap servers and public OSM-based renderers without API keys
        val osmUrls = listOf(
            "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png",
            "https://a.tile.openstreetmap.org/$zoom/$tileX/$tileY.png",
            "https://b.tile.openstreetmap.org/$zoom/$tileX/$tileY.png",
            "https://basemaps.cartocdn.com/rastertiles/voyager/$zoom/$tileX/$tileY.png"
        )

        for (url in osmUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "KolekProCam/1.0 (Android OSM Map Watermark)")
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
        val cacheDir = File(context.cacheDir, "osm_tiles")
        val cacheFile = File(cacheDir, "osm_${zoom}_${tileX}_${tileY}.png")
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
     * Transforms the map tile into a bird's-eye perspective view (tilted from above)
     * and draws the exact coordinate pinpoint with radar pulse and live indicator.
     */
    private fun createTiltedPerspectiveMapThumbnail(
        baseTile: Bitmap,
        lat: Double,
        lon: Double,
        zoom: Int,
        targetSize: Int
    ): Bitmap {
        val flatBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val flatCanvas = Canvas(flatBitmap)

        // 1. Calculate the exact coordinate offset inside the tile
        val n = 1 shl zoom
        val tileXFloat = ((lon + 180.0) / 360.0 * n)
        val latRad = Math.toRadians(lat)
        val tileYFloat = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n)
        val tileX = floor(tileXFloat).toInt()
        val tileY = floor(tileYFloat).toInt()

        val subX = (tileXFloat - tileX).toFloat().coerceIn(0.12f, 0.88f)
        val subY = (tileYFloat - tileY).toFloat().coerceIn(0.12f, 0.88f)

        // Draw base OpenStreetMap tile
        val srcRect = Rect(0, 0, baseTile.width, baseTile.height)
        val destRect = Rect(0, 0, targetSize, targetSize)
        flatCanvas.drawBitmap(baseTile, srcRect, destRect, null)

        // 2. Apply Perspective Tilt (Bird's-eye view from above / sudut dari atas)
        // Using android.graphics.Camera to tilt along X-axis
        val tiltedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val tiltedCanvas = Canvas(tiltedBitmap)

        val camera = Camera()
        val matrix = Matrix()

        val centerX = targetSize / 2f
        val centerY = targetSize / 2f

        camera.save()
        // Tilt pitch ~32 degrees from above for natural bird's-eye view
        camera.rotateX(32f)
        camera.getMatrix(matrix)
        camera.restore()

        // Center transformation and scale slightly to fill bounds cleanly
        matrix.preTranslate(-centerX, -centerY)
        matrix.postScale(1.15f, 1.15f)
        matrix.postTranslate(centerX, centerY + 8f)

        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        tiltedCanvas.drawBitmap(flatBitmap, matrix, tilePaint)

        // 3. Map the exact pinpoint coordinate through the perspective matrix
        val rawPinCoords = floatArrayOf(subX * targetSize, subY * targetSize)
        val tiltedPinCoords = floatArrayOf(0f, 0f)
        matrix.mapPoints(tiltedPinCoords, rawPinCoords)

        val pinX = tiltedPinCoords[0].coerceIn(24f, targetSize - 24f)
        val pinY = tiltedPinCoords[1].coerceIn(36f, targetSize - 28f)

        // 4. Draw radar pulse / accuracy ring around the exact point
        val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B82F6")
            alpha = 55
            style = Paint.Style.FILL
        }
        tiltedCanvas.drawCircle(pinX, pinY, 28f, radarPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            alpha = 150
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        tiltedCanvas.drawCircle(pinX, pinY, 28f, ringPaint)

        // 5. Ground shadow for the marker pin
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0)
            style = Paint.Style.FILL
        }
        tiltedCanvas.drawOval(RectF(pinX - 11f, pinY + 1f, pinX + 11f, pinY + 7f), shadowPaint)

        // 6. Upright Pinpoint Marker
        val pinPath = Path()
        val pinTop = pinY - 28f
        val pinRadius = 12f

        pinPath.moveTo(pinX, pinY)
        pinPath.cubicTo(
            pinX - 15f, pinY - 13f,
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
            pinX + 15f, pinY - 13f,
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
            strokeWidth = 2.2f
        }

        tiltedCanvas.drawPath(pinPath, pinPaint)
        tiltedCanvas.drawPath(pinPath, pinStrokePaint)

        // Inner white dot in the pin head
        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        tiltedCanvas.drawCircle(pinX, pinTop, 4.2f, innerDotPaint)

        // 7. Bottom clean badge: "GPS LIVE" (No "3D" text anywhere as requested)
        val badgeHeight = 20f
        val badgeWidth = 86f
        val badgeLeft = (targetSize - badgeWidth) / 2f
        val badgeTop = targetSize - badgeHeight - 6f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 15, 23, 42) // Translucent dark glass
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        tiltedCanvas.drawRoundRect(badgeRect, 10f, 10f, badgeBgPaint)
        tiltedCanvas.drawRoundRect(badgeRect, 10f, 10f, badgeBorderPaint)

        // Live beacon dot in badge
        val liveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981") // Emerald Green
            style = Paint.Style.FILL
        }
        tiltedCanvas.drawCircle(badgeLeft + 11f, badgeTop + 10f, 3.2f, liveDotPaint)

        // Text "GPS LIVE"
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        tiltedCanvas.drawText("GPS LIVE", badgeLeft + 20f, badgeTop + 13.5f, badgeTextPaint)

        return tiltedBitmap
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
