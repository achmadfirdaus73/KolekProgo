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
            Pair(216f to 0f, 216f to size.toFloat())
        )

        for ((start, end) in roads) {
            canvas.drawLine(start.first, start.second, end.first, end.second, roadOutlinePaint)
            canvas.drawLine(start.first, start.second, end.first, end.second, mainRoadPaint)
        }

        return bitmap
    }
}
