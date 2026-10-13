package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.PhotoQualityEngine
import com.example.camera.PhotoQualityStyle
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.PhotoRecord
import com.example.data.SettingsRepository
import com.example.location.LiveLocationData
import com.example.location.LocationManager
import com.example.location.MapTileHelper
import com.example.security.AntiFakeLocationDetector
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

    private val context = application.applicationContext
    private val locationManager = LocationManager(context, viewModelScope)
    private val settingsRepository = SettingsRepository(context)
    private val syncManager = SpreadsheetSyncManager(context)
    private val database = AppDatabase.getDatabase(context)
    private val photoDao = database.photoDao()

    val locationData: StateFlow<LiveLocationData> = locationManager.locationData
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _capturedResult = MutableStateFlow<WatermarkResult?>(null)
    val capturedResult: StateFlow<WatermarkResult?> = _capturedResult.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _currentMapThumbnail = MutableStateFlow<Bitmap?>(null)
    val currentMapThumbnail: StateFlow<Bitmap?> = _currentMapThumbnail.asStateFlow()

    private val _photoQualityStyle = MutableStateFlow(PhotoQualityStyle.SMART_HDR_ULTRA)
    val photoQualityStyle: StateFlow<PhotoQualityStyle> = _photoQualityStyle.asStateFlow()

    val allPhotos: StateFlow<List<PhotoRecord>> = photoDao.getAllPhotos()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var cameraController: CameraController? = null

    init {
        locationManager.startLocationUpdates()

        // Fetch map thumbnail dynamically when location updates
        viewModelScope.launch {
            locationData.collect { loc ->
                try {
                    val tile = MapTileHelper.fetchLocationMapTile(
                        context = context,
                        lat = loc.latitude,
                        lon = loc.longitude,
                        zoom = 16
                    )
                    _currentMapThumbnail.value = tile
                } catch (_: Exception) {}
            }
        }
    }

    fun refreshLocation() {
        locationManager.startLocationUpdates()
    }

    fun setCameraController(controller: CameraController) {
        this.cameraController = controller
    }

    fun cycleFlashMode() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        cameraController?.imageCapture?.flashMode = _flashMode.value
    }

    fun cyclePhotoQualityStyle() {
        val styles = PhotoQualityStyle.values()
        val currentIndex = styles.indexOf(_photoQualityStyle.value)
        val nextIndex = (currentIndex + 1) % styles.size
        _photoQualityStyle.value = styles[nextIndex]
    }

    fun toggleLensFacing() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun triggerShutter() {
        val controller = cameraController ?: return
        if (_isCapturing.value) return

        _isCapturing.value = true
        controller.takePhoto(
            onSuccess = { rawBitmap ->
                viewModelScope.launch(Dispatchers.Default) {
                    try {
                        val currentLocation = locationData.value
                        val secResult = AntiFakeLocationDetector.analyzeLocation(context, null)

                        val config = WatermarkConfig(
                            tagLabel = settings.value.companyTag,
                            qualityPercentage = settings.value.compressionQuality,
                            qualityStyle = _photoQualityStyle.value
                        )

                        val result = WatermarkRenderer.applyWatermarkAndCompress(
                            context = context,
                            sourceBitmap = rawBitmap,
                            locationData = currentLocation,
                            securityResult = secResult,
                            config = config,
                            customMapThumbnail = _currentMapThumbnail.value
                        )

                        _capturedResult.value = result
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        _isCapturing.value = false
                    }
                }
            },
            onError = { exc ->
                exc.printStackTrace()
                _isCapturing.value = false
            }
        )
    }

    fun dismissPreview() {
        _capturedResult.value = null
    }

    fun saveToGalleryAndLocal(note: String = "") {
        val currentResult = _capturedResult.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val currentLocation = locationData.value
            val secResult = currentLocation.securityResult
            val tag = settings.value.companyTag

            val savedFile = StorageHelper.saveToInternalStorage(
                context = context,
                jpegBytes = currentResult.jpegBytes,
                tagLabel = tag
            )

            StorageHelper.saveToPublicGallery(
                context = context,
                jpegBytes = currentResult.jpegBytes,
                tagLabel = tag
            )

            val photoRecord = PhotoRecord(
                filePath = savedFile.absolutePath,
                formattedDate = currentLocation.formattedDate,
                formattedTime = currentLocation.formattedTime,
                tagLabel = tag,
                address = currentLocation.address,
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                isMockDetected = secResult.isMockDetected,
                isTimeManipulated = secResult.isTimeManipulated,
                integrityHash = currentResult.integrityHash,
                signatureToken = secResult.signatureToken,
                note = note,
                fileSizeBytes = currentResult.fileSizeBytes,
                cameraStyle = _photoQualityStyle.value.name,
                isSynced = false
            )

            val insertedId = photoDao.insertPhoto(photoRecord)
            val insertedRecord = photoRecord.copy(id = insertedId)

            if (settings.value.autoSyncToSpreadsheet) {
                val syncResult = syncManager.syncPhoto(insertedRecord, settings.value)
                if (syncResult is SyncResult.Success) {
                    photoDao.updatePhoto(insertedRecord.copy(isSynced = true))
                }
            }

            withContext(Dispatchers.Main) {
                _capturedResult.value = null
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settingsRepository.updateSettings(newSettings)
    }

    fun manualSyncPhoto(photo: PhotoRecord, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val syncResult = syncManager.syncPhoto(photo, settings.value)
            val success = syncResult is SyncResult.Success
            if (success) {
                photoDao.updatePhoto(photo.copy(isSynced = true))
            }
            val msg = when (syncResult) {
                is SyncResult.Success -> syncResult.message
                is SyncResult.Error -> syncResult.errorMessage
            }
            withContext(Dispatchers.Main) {
                onComplete(success, msg)
            }
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            photoDao.deletePhotoById(photoId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationManager.stopLocationUpdates()
    }
}
