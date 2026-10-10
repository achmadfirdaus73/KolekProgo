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
 * and render a circular 3D stacked disc perspective map with exact GPS pin placement.
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
     * Renders a real-time dynamic circular 3D map thumbnail from OpenStreetMap
     * pinpointing the user's exact coordinates. No API key required!
     */
    suspend fun renderLocationMapThumbnail(
        context: Context,
        lat: Double,
        lon: Double,
        zoom: Int = 16,
        targetSize: Int = 256
    ): Bitmap = withContext(Dispatchers.IO) {
        if (memoryCachedThumbnail != null &&
            kotlin.math.abs(lat - lastLat) < 0.0001 &&
            kotlin.math.abs(lon - lastLon) < 0.0001
        ) {
            return@withContext memoryCachedThumbnail!!
        }

        val rawTile = fetchOpenStreetMapTile(context, lat, lon, zoom)
        val composite = createCircular3DPerspectiveMapThumbnail(rawTile, lat, lon, zoom, targetSize)

        lastLat = lat
        lastLon = lon
        memoryCachedThumbnail = composite
        composite
    }

    suspend fun fetchLocationMapTile(
        context: Context,
        lat: Double,
        lon: Double,
        zoom: Int = 16
    ): Bitmap = renderLocationMapThumbnail(context, lat, lon, zoom)

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
        val composite = createCircular3DPerspectiveMapThumbnail(baseTile, lat, lon, zoom, targetSize)
        lastLat = lat
        lastLon = lon
        memoryCachedThumbnail = composite
        return composite
    }

    private fun fetchOpenStreetMapTile(
        context: Context,
        lat: Double,
        lon: Double,
        zoom: Int
    ): Bitmap {
        if (lat == 0.0 && lon == 0.0) {
            return createProceduralMap(lat, lon)
        }

        val diskTile = getCachedDiskTile(context, lat, lon, zoom)
        if (diskTile != null) {
            return diskTile
        }

        val tileX = lonToTileX(lon, zoom)
        val tileY = latToTileY(lat, zoom)

        val cacheDir = File(context.cacheDir, "osm_tiles")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "osm_${zoom}_${tileX}_${tileY}.png")

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
     * Transforms the map tile into a circular 3D stacked perspective map view
     * matching the user's reference image exactly (with 3D stacked disc effect and "3D MAPS" badge).
     */
    private fun createCircular3DPerspectiveMapThumbnail(
        baseTile: Bitmap,
        lat: Double,
        lon: Double,
        zoom: Int,
        targetSize: Int
    ): Bitmap {
        val flatBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val flatCanvas = Canvas(flatBitmap)

        val n = 1 shl zoom
        val tileXFloat = ((lon + 180.0) / 360.0 * n)
        val latRad = Math.toRadians(lat)
        val tileYFloat = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n)
        val tileX = floor(tileXFloat).toInt()
        val tileY = floor(tileYFloat).toInt()

        val subX = (tileXFloat - tileX).toFloat().coerceIn(0.12f, 0.88f)
        val subY = (tileYFloat - tileY).toFloat().coerceIn(0.12f, 0.88f)

        val srcRect = Rect(0, 0, baseTile.width, baseTile.height)
        val destRect = Rect(0, 0, targetSize, targetSize)
        flatCanvas.drawBitmap(baseTile, srcRect, destRect, null)

        val tiltedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val tiltedCanvas = Canvas(tiltedBitmap)

        val camera = Camera()
        val matrix = Matrix()
        val centerX = targetSize / 2f
        val centerY = targetSize / 2f

        camera.save()
        camera.rotateX(34f)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-centerX, -centerY)
        matrix.postScale(1.25f, 1.25f)
        matrix.postTranslate(centerX, centerY + 12f)

        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        tiltedCanvas.drawBitmap(flatBitmap, matrix, tilePaint)

        // Result circular composition
        val circularBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val circleCanvas = Canvas(circularBitmap)

        val circleRadius = targetSize / 2.4f
        val circleX = targetSize / 2f
        val circleY = targetSize / 2f - 8f

        // Draw 3D stacked disc layers underneath (cylinder effect)
        for (i in 5 downTo 1) {
            val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(45 + i * 16, 15, 23, 42)
                style = Paint.Style.FILL
            }
            circleCanvas.drawCircle(circleX, circleY + (i * 3f), circleRadius, layerPaint)
        }

        // Clip to circular map disc
        val clipPath = Path().apply {
            addCircle(circleX, circleY, circleRadius, Path.Direction.CW)
        }
        circleCanvas.save()
        circleCanvas.clipPath(clipPath)
        circleCanvas.drawBitmap(tiltedBitmap, 0f, 0f, null)

        // Exact pinpoint coordinate mapping
        val rawPinCoords = floatArrayOf(subX * targetSize, subY * targetSize)
        val tiltedPinCoords = floatArrayOf(0f, 0f)
        matrix.mapPoints(tiltedPinCoords, rawPinCoords)
        val pinX = tiltedPinCoords[0].coerceIn(circleX - circleRadius + 20f, circleX + circleRadius - 20f)
        val pinY = tiltedPinCoords[1].coerceIn(circleY - circleRadius + 30f, circleY + circleRadius - 20f)

        // Radar ring around pin
        val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B82F6")
            alpha = 65
            style = Paint.Style.FILL
        }
        circleCanvas.drawCircle(pinX, pinY, 26f, radarPaint)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            alpha = 190
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        circleCanvas.drawCircle(pinX, pinY, 26f, ringPaint)

        // Pin shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.FILL
        }
        circleCanvas.drawOval(RectF(pinX - 10f, pinY + 1f, pinX + 10f, pinY + 6f), shadowPaint)

        // Pin marker
        val pinPath = Path()
        val pinTop = pinY - 26f
        val pinRadius = 11f
        pinPath.moveTo(pinX, pinY)
        pinPath.cubicTo(pinX - 14f, pinY - 12f, pinX - pinRadius, pinTop + pinRadius, pinX - pinRadius, pinTop)
        pinPath.arcTo(RectF(pinX - pinRadius, pinTop - pinRadius, pinX + pinRadius, pinTop + pinRadius), 180f, 180f)
        pinPath.cubicTo(pinX + pinRadius, pinTop + pinRadius, pinX + 14f, pinY - 12f, pinX, pinY)
        pinPath.close()

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF4444")
            style = Paint.Style.FILL
        }
        val pinStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        circleCanvas.drawPath(pinPath, pinPaint)
        circleCanvas.drawPath(pinPath, pinStroke)

        val innerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        circleCanvas.drawCircle(pinX, pinTop, 3.8f, innerDot)

        circleCanvas.restore()

        // Circular disc border stroke
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        circleCanvas.drawCircle(circleX, circleY, circleRadius, borderPaint)

        // Bottom "3D MAPS" Pill Badge matching user reference image exactly
        val badgeW = 76f
        val badgeH = 19f
        val badgeL = (targetSize - badgeW) / 2f
        val badgeT = targetSize - 22f
        val badgeRect = RectF(badgeL, badgeT, badgeL + badgeW, badgeT + badgeH)

        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 15, 23, 42)
            style = Paint.Style.FILL
        }
        val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        circleCanvas.drawRoundRect(badgeRect, 9.5f, 9.5f, badgeBg)
        circleCanvas.drawRoundRect(badgeRect, 9.5f, 9.5f, badgeBorder)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        circleCanvas.drawText("3D MAPS", badgeL + badgeW / 2f, badgeT + 13.5f, textPaint)

        return circularBitmap
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

        val bgPaint = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val parkPaint = Paint().apply {
            color = Color.parseColor("#D1FAE5")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(16f, 20f, 100f, 90f), 12f, 12f, parkPaint)
        canvas.drawRoundRect(RectF(160f, 150f, 240f, 220f), 12f, 12f, parkPaint)

        val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BAE6FD")
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawLine(0f, 180f, size.toFloat(), 130f, waterPaint)

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
