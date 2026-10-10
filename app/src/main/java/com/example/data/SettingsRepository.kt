package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val spreadsheetUrl: String = "",
    val companyTag: String = "KOLEKPRO",
    val compressionQuality: Int = 85,
    val resolutionPreset: String = "1080p", // "1080p", "720p", "ORIGINAL"
    val antiFakeEnforced: Boolean = true,
    val autoSyncOnSave: Boolean = true,
    val includeThumbnailInSync: Boolean = true,
    val colTimestamp: String = "A",
    val colDate: String = "B",
    val colTag: String = "C",
    val colAddress: String = "D",
    val colLatitude: String = "E",
    val colLongitude: String = "F",
    val colStatus: String = "G",
    val colIntegrityHash: String = "H",
    val colNote: String = "I",
    val colThumbnail: String = "J"
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kolekpro_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            spreadsheetUrl = AppConfig.SPREADSHEET_WEBAPP_URL,
            companyTag = prefs.getString("company_tag", com.example.config.AppConfig.DEFAULT_COMPANY_TAG) ?: com.example.config.AppConfig.DEFAULT_COMPANY_TAG,
            compressionQuality = prefs.getInt("compression_quality", com.example.config.AppConfig.DEFAULT_COMPRESSION_QUALITY),
            resolutionPreset = prefs.getString("resolution_preset", com.example.config.AppConfig.DEFAULT_RESOLUTION_PRESET) ?: com.example.config.AppConfig.DEFAULT_RESOLUTION_PRESET,
            antiFakeEnforced = prefs.getBoolean("anti_fake_enforced", true),
            autoSyncOnSave = prefs.getBoolean("auto_sync_on_save", true),
            includeThumbnailInSync = prefs.getBoolean("include_thumbnail_sync", true),
            colTimestamp = prefs.getString("col_timestamp", "A") ?: "A",
            colDate = prefs.getString("col_date", "B") ?: "B",
            colTag = prefs.getString("col_tag", "C") ?: "C",
            colAddress = prefs.getString("col_address", "D") ?: "D",
            colLatitude = prefs.getString("col_latitude", "E") ?: "E",
            colLongitude = prefs.getString("col_longitude", "F") ?: "F",
            colStatus = prefs.getString("col_status", "G") ?: "G",
            colIntegrityHash = prefs.getString("col_integrity_hash", "H") ?: "H",
            colNote = prefs.getString("col_note", "I") ?: "I",
            colThumbnail = prefs.getString("col_thumbnail", "J") ?: "J"
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putString("spreadsheet_url", newSettings.spreadsheetUrl.trim())
            putString("company_tag", newSettings.companyTag.trim().ifEmpty { "KOLEKPRO" })
            putInt("compression_quality", newSettings.compressionQuality)
            putString("resolution_preset", newSettings.resolutionPreset)
            putBoolean("anti_fake_enforced", newSettings.antiFakeEnforced)
            putBoolean("auto_sync_on_save", newSettings.autoSyncOnSave)
            putBoolean("include_thumbnail_sync", newSettings.includeThumbnailInSync)
            putString("col_timestamp", newSettings.colTimestamp)
            putString("col_date", newSettings.colDate)
            putString("col_tag", newSettings.colTag)
            putString("col_address", newSettings.colAddress)
            putString("col_latitude", newSettings.colLatitude)
            putString("col_longitude", newSettings.colLongitude)
            putString("col_status", newSettings.colStatus)
            putString("col_integrityHash", newSettings.colIntegrityHash)
            putString("col_note", newSettings.colNote)
            putString("col_thumbnail", newSettings.colThumbnail)
            apply()
        }
        _settings.value = newSettings
    }

    fun updateSpreadsheetUrl(url: String) {
        val current = _settings.value
        updateSettings(current.copy(spreadsheetUrl = url))
    }

    fun updateCompanyTag(tag: String) {
        val current = _settings.value
        updateSettings(current.copy(companyTag = tag))
    }
}
