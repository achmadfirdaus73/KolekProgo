package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.PhotoRecord
import com.example.data.SettingsRepository
import com.example.location.LiveLocationData
import com.example.location.LocationTracker
import com.example.sync.SpreadsheetSyncManager
import com.example.sync.SyncResult
import com.example.ui.components.CameraController
import com.example.util.StorageHelper
import com.example.watermark.WatermarkConfig
import com.example.watermark.WatermarkRenderer
import com.example.watermark.WatermarkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val photoDao = db.photoDao()
    private val settingsRepo = SettingsRepository(application)
    private val syncManager = SpreadsheetSyncManager(application)
    private val locationTracker = LocationTracker(application, viewModelScope)

    val locationData: StateFlow<LiveLocationData> = locationTracker.locationData
    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val allPhotos: StateFlow<List<PhotoRecord>> = photoDao.getAllPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _capturedResult = MutableStateFlow<WatermarkResult?>(null)
    val capturedResult: StateFlow<WatermarkResult?> = _capturedResult.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncSuccessMessage = MutableStateFlow<String?>(null)
    val syncSuccessMessage: StateFlow<String?> = _syncSuccessMessage.asStateFlow()

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage.asStateFlow()

    private val _isSavedToGallery = MutableStateFlow(false)
    val isSavedToGallery: StateFlow<Boolean> = _isSavedToGallery.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _gridEnabled = MutableStateFlow(false)
    val gridEnabled: StateFlow<Boolean> = _gridEnabled.asStateFlow()

    private val _selectedPhotoDetail = MutableStateFlow<PhotoRecord?>(null)
    val selectedPhotoDetail: StateFlow<PhotoRecord?> = _selectedPhotoDetail.asStateFlow()

    var cameraController: CameraController? = null

    init {
        locationTracker.startTracking()
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
    }

    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
    }

    fun toggleLensFacing() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun cycleFlashMode() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun toggleGrid() {
        _gridEnabled.value = !_gridEnabled.value
    }

    fun setSelectedPhotoDetail(photo: PhotoRecord?) {
        _selectedPhotoDetail.value = photo
    }

    fun dismissPreview() {
        _capturedResult.value = null
        _syncSuccessMessage.value = null
        _syncErrorMessage.value = null
        _isSavedToGallery.value = false
    }

    fun triggerShutter() {
        val controller = cameraController ?: return
        if (_isCapturing.value) return

        _isCapturing.value = true
        _syncSuccessMessage.value = null
        _syncErrorMessage.value = null
        _isSavedToGallery.value = false

        vibrate()

        controller.takePhoto(
            onSuccess = { bitmap ->
                processCapturedBitmap(bitmap)
            },
            onError = { _ ->
                _isCapturing.value = false
            }
        )
    }

    private fun processCapturedBitmap(sourceBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentLoc = locationData.value
            val currentSettings = settings.value

            val maxDim = when (currentSettings.resolutionPreset) {
                "720p" -> 1280
                "ORIGINAL" -> 0
                else -> 1920
            }

            val config = WatermarkConfig(
                tagLabel = currentSettings.companyTag,
                qualityPercentage = currentSettings.compressionQuality,
                maxDimension = maxDim,
                show3DMapBadge = true,
                showSecurityToken = true
            )

            val result = WatermarkRenderer.applyWatermarkAndCompress(
                context = getApplication(),
                sourceBitmap = sourceBitmap,
                locationData = currentLoc,
                securityResult = currentLoc.securityResult,
                config = config
            )

            _capturedResult.value = result
            _isCapturing.value = false
        }
    }

    fun saveToGalleryAndLocal(note: String) {
        val result = _capturedResult.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val currentLoc = locationData.value
            val currentSettings = settings.value

            // 1. Save to Internal Storage
            val internalFile = StorageHelper.saveToInternalStorage(
                getApplication(),
                result.jpegBytes,
                currentSettings.companyTag
            )

            // 2. Export to device MediaStore Gallery
            StorageHelper.exportToGallery(
                getApplication(),
                result.jpegBytes,
                currentSettings.companyTag
            )
            _isSavedToGallery.value = true

            // 3. Insert record in Room Database
            val record = PhotoRecord(
                filePath = internalFile.absolutePath,
                timestamp = currentLoc.timestamp,
                formattedDate = currentLoc.formattedDate,
                formattedTime = currentLoc.formattedTime,
                tagLabel = currentSettings.companyTag,
                latitude = currentLoc.latitude,
                longitude = currentLoc.longitude,
                address = currentLoc.address,
                isMockDetected = currentLoc.securityResult.isMockDetected,
                integrityHash = result.integrityHash,
                signatureToken = currentLoc.securityResult.signatureToken,
                note = note,
                fileSizeBytes = result.fileSizeBytes,
                syncStatus = PhotoRecord.SYNC_STATUS_PENDING
            )

            val insertedId = photoDao.insert(record)
            val savedRecord = record.copy(id = insertedId)

            // 4. Auto sync if enabled
            if (currentSettings.autoSyncOnSave && currentSettings.spreadsheetUrl.isNotBlank()) {
                syncSingleRecord(savedRecord)
            }
        }
    }

    fun syncCurrentPreviewToSpreadsheet(note: String) {
        val result = _capturedResult.value ?: return
        val currentSettings = settings.value

        if (currentSettings.spreadsheetUrl.isBlank()) {
            _syncErrorMessage.value = "URL Spreadsheet belum diisi di Pengaturan"
            return
        }

        _isSyncing.value = true
        _syncSuccessMessage.value = null
        _syncErrorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val currentLoc = locationData.value

            // Make sure file is also saved internally
            val internalFile = StorageHelper.saveToInternalStorage(
                getApplication(),
                result.jpegBytes,
                currentSettings.companyTag
            )

            val record = PhotoRecord(
                filePath = internalFile.absolutePath,
                timestamp = currentLoc.timestamp,
                formattedDate = currentLoc.formattedDate,
                formattedTime = currentLoc.formattedTime,
                tagLabel = currentSettings.companyTag,
                latitude = currentLoc.latitude,
                longitude = currentLoc.longitude,
                address = currentLoc.address,
                isMockDetected = currentLoc.securityResult.isMockDetected,
                integrityHash = result.integrityHash,
                signatureToken = currentLoc.securityResult.signatureToken,
                note = note,
                fileSizeBytes = result.fileSizeBytes,
                syncStatus = PhotoRecord.SYNC_STATUS_PENDING
            )

            val id = photoDao.insert(record)
            val insertedRecord = record.copy(id = id)

            when (val syncRes = syncManager.sendPhotoRecord(insertedRecord, currentSettings)) {
                is SyncResult.Success -> {
                    _syncSuccessMessage.value = syncRes.message
                    photoDao.update(
                        insertedRecord.copy(
                            syncStatus = PhotoRecord.SYNC_STATUS_SYNCED,
                            syncTimestamp = System.currentTimeMillis()
                        )
                    )
                }
                is SyncResult.Error -> {
                    _syncErrorMessage.value = syncRes.errorMessage
                    photoDao.update(
                        insertedRecord.copy(
                            syncStatus = PhotoRecord.SYNC_STATUS_FAILED,
                            syncErrorMessage = syncRes.errorMessage
                        )
                    )
                }
            }
            _isSyncing.value = false
        }
    }

    fun syncSingleRecord(record: PhotoRecord) {
        val currentSettings = settings.value
        if (currentSettings.spreadsheetUrl.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            when (val syncRes = syncManager.sendPhotoRecord(record, currentSettings)) {
                is SyncResult.Success -> {
                    photoDao.update(
                        record.copy(
                            syncStatus = PhotoRecord.SYNC_STATUS_SYNCED,
                            syncTimestamp = System.currentTimeMillis(),
                            syncErrorMessage = null
                        )
                    )
                }
                is SyncResult.Error -> {
                    photoDao.update(
                        record.copy(
                            syncStatus = PhotoRecord.SYNC_STATUS_FAILED,
                            syncErrorMessage = syncRes.errorMessage
                        )
                    )
                }
            }
        }
    }

    fun syncAllPendingPhotos(onCompleted: (successCount: Int, failedCount: Int) -> Unit) {
        val currentSettings = settings.value
        if (currentSettings.spreadsheetUrl.isBlank()) {
            onCompleted(0, 0)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val pendingList = photoDao.getPendingSyncPhotosList()
            var success = 0
            var failed = 0

            for (photo in pendingList) {
                when (val res = syncManager.sendPhotoRecord(photo, currentSettings)) {
                    is SyncResult.Success -> {
                        photoDao.update(
                            photo.copy(
                                syncStatus = PhotoRecord.SYNC_STATUS_SYNCED,
                                syncTimestamp = System.currentTimeMillis()
                            )
                        )
                        success++
                    }
                    is SyncResult.Error -> {
                        photoDao.update(
                            photo.copy(
                                syncStatus = PhotoRecord.SYNC_STATUS_FAILED,
                                syncErrorMessage = res.errorMessage
                            )
                        )
                        failed++
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onCompleted(success, failed)
            }
        }
    }

    fun deletePhoto(photo: PhotoRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            photoDao.delete(photo)
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settingsRepo.updateSettings(newSettings)
    }

    fun updateSpreadsheetUrl(url: String) {
        settingsRepo.updateSpreadsheetUrl(url)
    }

    fun updateCompanyTag(tag: String) {
        settingsRepo.updateCompanyTag(tag)
    }

    fun testSpreadsheetConnection(url: String, onResult: (SyncResult) -> Unit) {
        viewModelScope.launch {
            val res = syncManager.testConnection(url)
            onResult(res)
        }
    }

    private fun vibrate() {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) {}
    }
}
