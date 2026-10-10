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

sealed class SyncResult {
    data class Success(val message: String, val rowId: String? = null) : SyncResult()
    data class Error(val errorMessage: String) : SyncResult()
}

class SpreadsheetSyncManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun sendPhotoRecord(
        photo: PhotoRecord,
        settings: AppSettings
    ): SyncResult = withContext(Dispatchers.IO) {
        val url = if (AppConfig.isSpreadsheetConfigured()) {
            AppConfig.SPREADSHEET_WEBAPP_URL.trim()
        } else if (settings.spreadsheetUrl.isNotBlank() && !settings.spreadsheetUrl.contains("YOUR_APPS_SCRIPT_ID_HERE")) {
            settings.spreadsheetUrl.trim()
        } else {
            return@withContext SyncResult.Error("URL Web App belum diisi di kode (AppConfig.kt). Buka file AppConfig.kt dan tempelkan URL Web App Anda.")
        }

        try {
            // Optional mini base64 thumbnail for spreadsheet
            var thumbnailBase64 = ""
            if (settings.includeThumbnailInSync && photo.filePath.isNotEmpty()) {
                val file = File(photo.filePath)
                if (file.exists()) {
                    thumbnailBase64 = generateMiniThumbnailBase64(file)
                }
            }

            val statusText = if (photo.isMockDetected) {
                "PERINGATAN: FAKE GPS TERDETEKSI"
            } else {
                "ASLI TERVERIFIKASI"
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
                put("integrityHash", photo.integrityHash)
                put("signatureToken", photo.signatureToken)
                put("note", photo.note)
                put("fileSizeBytes", photo.fileSizeBytes)
                put("columnMapping", JSONObject().apply {
                    put("colTimestamp", settings.colTimestamp)
                    put("colDate", settings.colDate)
                    put("colTag", settings.colTag)
                    put("colAddress", settings.colAddress)
                    put("colLatitude", settings.colLatitude)
                    put("colLongitude", settings.colLongitude)
                    put("colStatus", settings.colStatus)
                    put("colIntegrityHash", settings.colIntegrityHash)
                    put("colNote", settings.colNote)
                    put("colThumbnail", settings.colThumbnail)
                })
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

    suspend fun testConnection(url: String): SyncResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return@withContext SyncResult.Error("URL Spreadsheet tidak boleh kosong.")
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext SyncResult.Error("URL harus diawali dengan https://")
        }

        try {
            val testJson = JSONObject().apply {
                put("action", "test_connection")
                put("timestamp", "TEST KONEKSI KOLEKPRO")
                put("date", "Tes Tanggal")
                put("tag", "TEST_KOLEKPRO")
                put("address", "Uji Coba Integrasi Spreadsheet Berhasil")
                put("latitude", -6.175392)
                put("longitude", 106.827153)
                put("status", "KONEKSI BERHASIL")
                put("note", "Pengujian baris berhasil diterima dari aplikasi Android")
            }

            val body = testJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(trimmed)
                .post(body)
                .addHeader("User-Agent", "KolekProCam-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code == 302) {
                SyncResult.Success("Koneksi ke Google Sheets Berhasil! Endpoint merespon dengan baik.")
            } else {
                SyncResult.Error("Endpoint merespon dengan error HTTP ${response.code}")
            }
        } catch (e: Exception) {
            SyncResult.Error("Tidak dapat terhubung ke URL: ${e.localizedMessage}")
        }
    }

    private fun generateMiniThumbnailBase64(file: File): String {
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, options) ?: return ""
            val thumb = Bitmap.createScaledBitmap(bmp, 120, 120, true)
            val stream = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 60, stream)
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
 * KOLEKPRO CAM - Google Sheets Webhook Script
 * --------------------------------------------
 * Cara Pemasangan:
 * 1. Di Google Sheets, klik menu 'Ekstensi' > 'Apps Script'
 * 2. Hapus semua teks yang ada, lalu paste seluruh skrip ini
 * 3. Klik tombol 'Deploy' (Terapkan) > 'New Deployment' (Penerapan baru)
 * 4. Pilih tipe: 'Web app' (Aplikasi web)
 * 5. Set 'Execute as' (Jalankan sebagai): 'Me' (Saya)
 * 6. Set 'Who has access' (Yang memiliki akses): 'Anyone' (Siapa saja)
 * 7. Klik 'Deploy', izinkan izin Google, lalu SALIN Web App URL (berakhiran /exec)
 * 8. Tempelkan URL tersebut ke dalam aplikasi KolekPro Cam!
 */

function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getActiveSheet();
    
    // Pastikan header sudah ada jika sheet baru kosong
    if (sheet.getLastRow() === 0) {
      sheet.appendRow([
        "Waktu & Tanggal",
        "Tanggal",
        "Unit/Tag",
        "Alamat Lengkap",
        "Latitude",
        "Longitude",
        "Status Keaslian",
        "Hash Integritas (Anti-Tamper)",
        "Catatan",
        "Pratinjau Foto"
      ]);
      // Format header
      sheet.getRange(1, 1, 1, 10).setFontWeight("bold").setBackground("#0F172A").setFontColor("#FFFFFF");
    }

    var contents = e.postData.contents;
    var data = JSON.parse(contents);
    
    var time = data.timestamp || new Date().toLocaleString("id-ID");
    var date = data.date || "";
    var tag = data.tag || "KOLEKPRO";
    var address = data.address || "";
    var lat = data.latitude || "";
    var lon = data.longitude || "";
    var status = data.status || (data.isMock ? "PERINGATAN: FAKE GPS" : "ASLI TERVERIFIKASI");
    var integrityHash = data.integrityHash || "";
    var note = data.note || "";
    var thumbFormula = "";
    
    if (data.thumbnailBase64 && data.thumbnailBase64.length > 0) {
      thumbFormula = "Foto Tersimpan (" + data.fileSizeBytes + " B)";
    }

    sheet.appendRow([
      time,
      date,
      tag,
      address,
      lat,
      lon,
      status,
      integrityHash,
      note,
      thumbFormula
    ]);

    return ContentService.createTextOutput(JSON.stringify({
      "status": "success",
      "row": sheet.getLastRow(),
      "message": "Data foto berhasil direkam di Google Sheets"
    })).setMimeType(ContentService.MimeType.JSON);

  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({
      "status": "error",
      "message": err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  return ContentService.createTextOutput("KolekPro Cam Google Sheets Webhook Aktif dan Siap Digunakan!");
}
            """.trimIndent()
        }
    }
}
