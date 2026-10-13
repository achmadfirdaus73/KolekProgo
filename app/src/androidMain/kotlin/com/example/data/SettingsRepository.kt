package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val companyTag: String = AppConfig.DEFAULT_COMPANY_TAG,
    val spreadsheetUrl: String = AppConfig.SPREADSHEET_WEBAPP_URL,
    val compressionQuality: Int = AppConfig.DEFAULT_COMPRESSION_QUALITY,
    val resolutionPreset: String = AppConfig.DEFAULT_RESOLUTION_PRESET,
    val autoSyncToSpreadsheet: Boolean = true,
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
        context.getSharedPreferences("kolekpro_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            companyTag = prefs.getString("company_tag", AppConfig.DEFAULT_COMPANY_TAG) ?: AppConfig.DEFAULT_COMPANY_TAG,
            spreadsheetUrl = prefs.getString("spreadsheet_url", AppConfig.SPREADSHEET_WEBAPP_URL) ?: AppConfig.SPREADSHEET_WEBAPP_URL,
            compressionQuality = prefs.getInt("compression_quality", AppConfig.DEFAULT_COMPRESSION_QUALITY),
            resolutionPreset = prefs.getString("resolution_preset", AppConfig.DEFAULT_RESOLUTION_PRESET) ?: AppConfig.DEFAULT_RESOLUTION_PRESET,
            autoSyncToSpreadsheet = prefs.getBoolean("auto_sync", true),
            includeThumbnailInSync = prefs.getBoolean("include_thumb", true)
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putString("company_tag", newSettings.companyTag)
            putString("spreadsheet_url", newSettings.spreadsheetUrl)
            putInt("compression_quality", newSettings.compressionQuality)
            putString("resolution_preset", newSettings.resolutionPreset)
            putBoolean("auto_sync", newSettings.autoSyncToSpreadsheet)
            putBoolean("include_thumb", newSettings.includeThumbnailInSync)
            apply()
        }
        _settings.value = newSettings
    }
}
