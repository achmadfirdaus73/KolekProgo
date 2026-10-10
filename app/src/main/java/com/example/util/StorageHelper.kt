package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.camera.PhotoQualityEngine
import com.example.camera.PhotoQualityStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageHelper {

    suspend fun saveToInternalStorage(
        context: Context,
        jpegBytes: ByteArray,
        tagLabel: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        style: PhotoQualityStyle = PhotoQualityStyle.SMART_HDR_ULTRA,
        timestampMs: Long = System.currentTimeMillis()
    ): File = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "kolekpro_photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMs))
        val fileName = "${tagLabel}_TimeMark_${timestamp}_${System.currentTimeMillis()}.jpg"
        val file = File(photosDir, fileName)
        FileOutputStream(file).use { out ->
            out.write(jpegBytes)
            out.flush()
        }

        // Inject calibrated camera EXIF metadata into the file
        PhotoQualityEngine.injectCalibratedExif(
            file = file,
            latitude = latitude,
            longitude = longitude,
            style = style,
            timestamp = timestampMs
        )

        file
    }

    suspend fun exportToGallery(
        context: Context,
        savedFile: File,
        tagLabel: String,
        style: PhotoQualityStyle = PhotoQualityStyle.SMART_HDR_ULTRA
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val displayName = "${tagLabel}_TimeMark_${timestamp}.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (style != PhotoQualityStyle.OFF) {
                    put(MediaStore.Images.Media.DESCRIPTION, "KolekPro Geo-Verified • ${style.title}")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KolekPro")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(savedFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    outputStream.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                return@withContext uri
            }
        } catch (_: Exception) {}
        null
    }

    fun shareImage(context: Context, file: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Foto Watermark"))
        } catch (_: Exception) {}
    }
}
