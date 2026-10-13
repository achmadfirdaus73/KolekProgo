package com.example.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

object MapTileHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var memoryCachedThumbnail: Bitmap? = null
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

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
        lastLat = lat
        lastLon = lon
        memoryCachedThumbnail = rawTile
        rawTile
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
        return getRawTileSync(context, lat, lon, zoom)
    }

    fun getRawTileSync(context: Context, lat: Double, lon: Double, zoom: Int = 16): Bitmap {
        val diskTile = getCachedDiskTile(context, lat, lon, zoom)
        return diskTile ?: createProceduralMap(lat, lon)
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
            "https://b.tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
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

        // Tactical dark blueprint background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0A0F1D")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Isometric / Angled grid lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        var x = 0f
        while (x < size * 2) {
            canvas.drawLine(x, 0f, 0f, x, gridPaint)
            x += 24f
        }

        // Angled / Isometric building blocks (Tactical 3D projection)
        val buildingFill = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        val buildingTop = Paint().apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.FILL
        }
        val neonOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val blocks = listOf(
            RectF(30f, 40f, 90f, 110f),
            RectF(120f, 30f, 200f, 100f),
            RectF(40f, 140f, 130f, 210f),
            RectF(150f, 130f, 220f, 200f)
        )

        for (block in blocks) {
            val shadowRect = RectF(block.left + 6f, block.top + 6f, block.right + 6f, block.bottom + 6f)
            canvas.drawRect(shadowRect, buildingFill)
            canvas.drawRect(block, buildingTop)
            canvas.drawRect(block, neonOutline)
        }

        // Angled roads / highways with glowing center lines
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 16f
        }
        val roadCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
        }

        canvas.drawLine(0f, 128f, size.toFloat(), 128f, roadPaint)
        canvas.drawLine(0f, 128f, size.toFloat(), 128f, roadCenterPaint)

        canvas.drawLine(128f, 0f, 128f, size.toFloat(), roadPaint)
        canvas.drawLine(128f, 0f, 128f, size.toFloat(), roadCenterPaint)

        // Center tactical crosshair / radar scope (Broadcast stadium aerial view HUD)
        val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            alpha = 180
        }
        canvas.drawCircle(size / 2f, size / 2f, 45f, radarPaint)
        canvas.drawCircle(size / 2f, size / 2f, 85f, radarPaint)
        
        val centerPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 5f, centerPointPaint)

        return bitmap
    }
}
