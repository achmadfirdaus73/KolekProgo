package com.example.config

/**
 * PUSAT KONFIGURASI WEB APP GOOGLE SPREADSHEET & APLIKASI
 * --------------------------------------------------------
 * Anda dapat langsung menempelkan URL Web App Google Apps Script Anda di bawah ini.
 * URL ini akan otomatis digunakan oleh aplikasi untuk mengirimkan data foto watermark.
 */
object AppConfig {

    /**
     * TEMPELKAN URL GOOGLE APPS SCRIPT WEB APP DI SINI:
     * Contoh: "https://script.google.com/macros/s/AKfycby.../exec"
     */
    const val SPREADSHEET_WEBAPP_URL: String = "https://script.google.com/macros/s/YOUR_APPS_SCRIPT_ID_HERE/exec"

    /**
     * Label watermark & unit default
     */
    const val DEFAULT_COMPANY_TAG: String = "KOLEKPRO"

    /**
     * Kualitas kompresi foto JPEG (40 - 100)
     */
    const val DEFAULT_COMPRESSION_QUALITY: Int = 85

    /**
     * Preset resolusi ("1080p", "720p", "ORIGINAL")
     */
    const val DEFAULT_RESOLUTION_PRESET: String = "1080p"

    /**
     * Memeriksa apakah URL sudah diisi oleh pengguna (bukan placeholder default)
     */
    fun isSpreadsheetConfigured(): Boolean {
        val url = SPREADSHEET_WEBAPP_URL.trim()
        return url.startsWith("https://") && !url.contains("YOUR_APPS_SCRIPT_ID_HERE")
    }
}
