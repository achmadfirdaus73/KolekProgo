package com.example.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.config.AppConfig
import com.example.data.AppSettings
import com.example.data.PhotoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min

sealed class SyncResult {
    data class Success(val message: String, val rowId: String? = null) : SyncResult()
    data class Error(val errorMessage: String) : SyncResult()
}

class SpreadsheetSyncManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun syncPhoto(photo: PhotoRecord, settings: AppSettings): SyncResult = withContext(Dispatchers.IO) {
        val url = settings.spreadsheetUrl.trim()
        if (url.isEmpty() || !url.startsWith("https://")) {
            return@withContext SyncResult.Error("URL Webhook Google Sheets belum diatur.")
        }

        try {
            var thumbnailBase64 = ""
            if (settings.includeThumbnailInSync && photo.filePath.isNotEmpty()) {
                val file = File(photo.filePath)
                if (file.exists()) {
                    thumbnailBase64 = generateHighResPhotoBase64(file)
                }
            }

            val statusText = when {
                photo.isTimeManipulated && photo.isMockDetected -> "PERINGATAN: WAKTU & GPS PALSU"
                photo.isTimeManipulated -> "PERINGATAN: WAKTU DIMANIPULASI"
                photo.isMockDetected -> "PERINGATAN: FAKE GPS TERDETEKSI"
                else -> "ASLI TERVERIFIKASI"
            }

            val json = JSONObject().apply {
                put("action", "insert_record")
                put("timestamp", "${photo.formattedDate} ${photo.formattedTime}")
                put("date", photo.formattedDate)
                put("time", photo.formattedTime)
                put("tag", photo.tagLabel)
                put("address", photo.address)
                put("latitude", photo.latitude)
                put("longitude", photo.longitude)
                put("status", statusText)
                put("isMock", photo.isMockDetected)
                put("isTimeManipulated", photo.isTimeManipulated)
                put("deviceModel", photo.cameraStyle)
                put("cameraSpecs", "Smart HDR High Clarity")
                put("integrityHash", photo.integrityHash)
                put("signatureToken", photo.signatureToken)
                put("note", photo.note)
                put("fileSizeBytes", photo.fileSizeBytes)
                if (thumbnailBase64.isNotEmpty()) {
                    put("thumbnailBase64", thumbnailBase64)
                }
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", "KolekProCam-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful || response.code == 302) {
                SyncResult.Success(
                    message = "Berhasil disinkronkan ke Spreadsheet",
                    rowId = extractRowOrStatus(responseBody)
                )
            } else {
                SyncResult.Error("Gagal sinkron (HTTP ${response.code}): ${response.message}")
            }
        } catch (e: Exception) {
            SyncResult.Error("Koneksi gagal: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun generateHighResPhotoBase64(file: File): String {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val maxDim = 1440
            var inSampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return ""

            val w = bmp.width
            val h = bmp.height
            val scaledBmp = if (w > maxDim || h > maxDim) {
                val ratio = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
                val targetW = (w * ratio).toInt()
                val targetH = (h * ratio).toInt()
                Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            } else {
                bmp
            }

            val stream = ByteArrayOutputStream()
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractRowOrStatus(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("row", json.optString("status", "OK"))
        } catch (_: Exception) {
            "OK"
        }
    }

    companion object {
        fun getGoogleAppsScriptTemplate(): String {
            return """
/**
 * KOLEKPRO CAM - Google Sheets & Drive Webhook Script
 */
function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getActiveSheet();
    var contents = e.postData.contents;
    var data = JSON.parse(contents);
    sheet.appendRow([data.timestamp, data.address, data.latitude, data.longitude, data.status]);
    return ContentService.createTextOutput(JSON.stringify({"status":"success"})).setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({"status":"error"})).setMimeType(ContentService.MimeType.JSON);
  }
}
            """.trimIndent()
        }
    }
}
