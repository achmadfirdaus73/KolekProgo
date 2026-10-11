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
import com.example.config.AppConfig
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.PhotoRecord
import com.example.data.SettingsRepository
import com.example.location.LiveLocationData
import com.example.location.LocationTracker
import com.example.location.MapTileHelper
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
import kotlinx.coroutines.flow.collectLatest
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

    // Real dynamic pinpoint map thumbnail
    private val _currentMapThumbnail = MutableStateFlow<Bitmap?>(null)
    val currentMapThumbnail: StateFlow<Bitmap?> = _currentMapThumbnail.asStateFlow()

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

    // Exposure boost level (0, 2, 4) to brighten dark camera
    private val _exposureBoost = MutableStateFlow(0)
    val exposureBoost: StateFlow<Int> = _exposureBoost.asStateFlow()

    private val _selectedPhotoDetail = MutableStateFlow<PhotoRecord?>(null)
    val selectedPhotoDetail: StateFlow<PhotoRecord?> = _selectedPhotoDetail.asStateFlow()

    // Computational photography HDR quality styles
    private val _photoQualityStyle = MutableStateFlow(com.example.camera.PhotoQualityStyle.SMART_HDR_ULTRA)
    val photoQualityStyle: StateFlow<com.example.camera.PhotoQualityStyle> = _photoQualityStyle.asStateFlow()
    val iphoneStyle: StateFlow<com.example.camera.PhotoQualityStyle> = _photoQualityStyle.asStateFlow()

    fun setPhotoQualityStyle(style: com.example.camera.PhotoQualityStyle) {
        _photoQualityStyle.value = style
    }

    fun cyclePhotoQualityStyle() {
        val styles = com.example.camera.PhotoQualityStyle.values()
        val nextIndex = (styles.indexOf(_photoQualityStyle.value) + 1) % styles.size
        _photoQualityStyle.value = styles[nextIndex]
    }

    fun cycleIPhoneStyle() = cyclePhotoQualityStyle()

    var cameraController: CameraController? = null

    init {
        locationTracker.startTracking()

        // Observe location updates and dynamically generate the real map thumbnail with pinpoint
        viewModelScope.launch(Dispatchers.IO) {
            locationData.collectLatest { loc ->
                try {
                    val bmp = MapTileHelper.renderLocationMapThumbnail(
                        context = getApplication(),
                        lat = loc.latitude,
                        lon = loc.longitude,
                        zoom = 16,
                        targetSize = 256
                    )
                    _currentMapThumbnail.value = bmp
                } catch (_: Exception) {}
            }
        }
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

    fun cycleExposureBoost() {
        _exposureBoost.value = when (_exposureBoost.value) {
            0 -> 2
            2 -> 4
            else -> 0
        }
        cameraController?.boostExposure(_exposureBoost.value)
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
                else -> 3840 // 4K crystal-clear maximum resolution
            }

            val config = WatermarkConfig(
                tagLabel = currentSettings.companyTag,
                qualityPercentage = currentSettings.compressionQuality,
                maxDimension = maxDim,
                showDynamicMap = true,
                showSecurityToken = true,
                qualityStyle = _photoQualityStyle.value
            )

            val currentMap = _currentMapThumbnail.value

            val result = WatermarkRenderer.applyWatermarkAndCompress(
                context = getApplication(),
                sourceBitmap = sourceBitmap,
                locationData = currentLoc,
                securityResult = currentLoc.securityResult,
                config = config,
                customMapThumbnail = currentMap
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
            val currentStyle = _photoQualityStyle.value

            // 1. Save to Internal Storage with Calibrated EXIF Metadata
            val internalFile = StorageHelper.saveToInternalStorage(
                context = getApplication(),
                jpegBytes = result.jpegBytes,
                tagLabel = currentSettings.companyTag,
                latitude = currentLoc.latitude,
                longitude = currentLoc.longitude,
                style = currentStyle,
                timestampMs = currentLoc.timestamp
            )

            // 2. Export to device MediaStore Gallery
            StorageHelper.exportToGallery(
                context = getApplication(),
                savedFile = internalFile,
                tagLabel = currentSettings.companyTag,
                style = currentStyle
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
                isTimeManipulated = currentLoc.securityResult.isTimeManipulated,
                cameraStyle = currentStyle.title,
                integrityHash = result.integrityHash,
                signatureToken = currentLoc.securityResult.signatureToken,
                note = note,
                fileSizeBytes = result.fileSizeBytes,
                syncStatus = PhotoRecord.SYNC_STATUS_PENDING
            )

            val insertedId = photoDao.insert(record)
            val savedRecord = record.copy(id = insertedId)

            // 4. Auto sync in background if configured in AppConfig or settings
            val hasSpreadsheet = AppConfig.isSpreadsheetConfigured() || currentSettings.spreadsheetUrl.isNotBlank()
            if (currentSettings.autoSyncOnSave && hasSpreadsheet) {
                syncSingleRecord(savedRecord)
            }
        }
    }

    fun syncCurrentPreviewToSpreadsheet(note: String) {
        val result = _capturedResult.value ?: return
        if (_isSyncing.value) return

        val currentLoc = locationData.value
        val currentSettings = settings.value
        val isConfigured = AppConfig.isSpreadsheetConfigured() || currentSettings.spreadsheetUrl.isNotBlank()

        if (!isConfigured) {
            _syncErrorMessage.value = "URL Spreadsheet belum diset di AppConfig.kt"
            return
        }

        _isSyncing.value = true
        _syncSuccessMessage.value = null
        _syncErrorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            // Save locally first if not saved
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
                isTimeManipulated = currentLoc.securityResult.isTimeManipulated,
                cameraStyle = _photoQualityStyle.value.title,
                integrityHash = result.integrityHash,
                signatureToken = currentLoc.securityResult.signatureToken,
                note = note,
                fileSizeBytes = result.fileSizeBytes,
                syncStatus = PhotoRecord.SYNC_STATUS_PENDING
            )

            val insertedId = photoDao.insert(record)
            val savedRecord = record.copy(id = insertedId)

            val syncResult = syncManager.sendPhotoRecord(
                photo = savedRecord,
                settings = currentSettings
            )

            withContext(Dispatchers.Main) {
                _isSyncing.value = false
                when (syncResult) {
                    is SyncResult.Success -> {
                        _syncSuccessMessage.value = "Data berhasil disinkronkan"
                        viewModelScope.launch(Dispatchers.IO) {
                            photoDao.update(
                                savedRecord.copy(
                                    syncStatus = PhotoRecord.SYNC_STATUS_SYNCED,
                                    syncTimestamp = System.currentTimeMillis(),
                                    syncErrorMessage = null
                                )
                            )
                        }
                    }
                    is SyncResult.Error -> {
                        _syncErrorMessage.value = syncResult.errorMessage
                        viewModelScope.launch(Dispatchers.IO) {
                            photoDao.update(
                                savedRecord.copy(
                                    syncStatus = PhotoRecord.SYNC_STATUS_FAILED,
                                    syncErrorMessage = syncResult.errorMessage
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun syncSingleRecord(record: PhotoRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = settings.value
            val syncResult = syncManager.sendPhotoRecord(
                photo = record,
                settings = currentSettings
            )

            when (syncResult) {
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
                            syncErrorMessage = syncResult.errorMessage
                        )
                    )
                }
            }
        }
    }

    fun syncAllPendingPhotos(onCompleted: (success: Int, failed: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = photoDao.getPendingSyncPhotosList()
            val currentSettings = settings.value
            var success = 0
            var failed = 0

            for (photo in pending) {
                val res = syncManager.sendPhotoRecord(photo, settings = currentSettings)
                if (res is SyncResult.Success) {
                    photoDao.update(
                        photo.copy(
                            syncStatus = PhotoRecord.SYNC_STATUS_SYNCED,
                            syncErrorMessage = null,
                            syncTimestamp = System.currentTimeMillis()
                        )
                    )
                    success++
                } else if (res is SyncResult.Error) {
                    photoDao.update(
                        photo.copy(
                            syncStatus = PhotoRecord.SYNC_STATUS_FAILED,
                            syncErrorMessage = res.errorMessage
                        )
                    )
                    failed++
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

    fun updateCompanyTag(tag: String) {
        settingsRepo.updateCompanyTag(tag)
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
