package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageHelper {

    fun saveToInternalStorage(
        context: Context,
        jpegBytes: ByteArray,
        tagLabel: String
    ): File {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${tagLabel}_${timeStamp}.jpg"
        val file = File(dir, fileName)

        FileOutputStream(file).use { out ->
            out.write(jpegBytes)
            out.flush()
        }
        return file
    }

    fun saveToPublicGallery(
        context: Context,
        jpegBytes: ByteArray,
        tagLabel: String
    ): Boolean {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${tagLabel}_${timeStamp}.jpg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KolekPro")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jpegBytes)
                        out.flush()
                    }
                    true
                } else false
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "KolekPro")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    out.write(jpegBytes)
                    out.flush()
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
