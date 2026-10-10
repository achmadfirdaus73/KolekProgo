package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    val imageCapture: ImageCapture,
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
) {
    var camera: Camera? = null

    fun boostExposure(step: Int = 2) {
        val cam = camera ?: return
        try {
            val range = cam.cameraInfo.exposureState.exposureCompensationRange
            val current = cam.cameraInfo.exposureState.exposureCompensationIndex
            val target = (current + step).coerceIn(range.lower, range.upper)
            cam.cameraControl.setExposureCompensationIndex(target)
        } catch (e: Exception) {
            Log.w("CameraController", "Exposure adjustment not supported", e)
        }
    }

    fun takePhoto(
        onSuccess: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(image)
                        onSuccess(bitmap)
                    } catch (e: Exception) {
                        onError(e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotationDegrees = image.imageInfo.rotationDegrees
        return if (rotationDegrees != 0 && originalBitmap != null) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
        } else {
            originalBitmap
        }
    }
}

@Composable
fun CameraPreviewView(
    lensFacing: Int,
    flashMode: Int,
    exposureBoostIndex: Int = 0,
    onControllerReady: (CameraController) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .build()
    }

    val controller = remember {
        CameraController(context, imageCapture)
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    // Function to bind camera use cases
    fun bindCamera(previewView: PreviewView, cameraProvider: ProcessCameraProvider) {
        try {
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            // Verify if lens is available, fallback if needed
            val targetSelector = if (cameraProvider.hasCamera(cameraSelector)) {
                cameraSelector
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                targetSelector,
                preview,
                imageCapture
            )
            controller.camera = camera
            onControllerReady(controller)

            // Auto-trigger center metering and exposure
            previewView.post {
                try {
                    val w = previewView.width.toFloat()
                    val h = previewView.height.toFloat()
                    if (w > 0 && h > 0) {
                        val factory = previewView.meteringPointFactory
                        val centerPoint = factory.createPoint(w / 2f, h / 2f)
                        val action = FocusMeteringAction.Builder(centerPoint).build()
                        camera.cameraControl.startFocusAndMetering(action)
                    }

                    // Apply exposure compensation boost if requested
                    if (exposureBoostIndex != 0) {
                        val state = camera.cameraInfo.exposureState
                        if (state.isExposureCompensationSupported) {
                            val range = state.exposureCompensationRange
                            val target = exposureBoostIndex.coerceIn(range.lower, range.upper)
                            camera.cameraControl.setExposureCompensationIndex(target)
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("CameraPreviewView", "Error binding camera preview", e)
        }
    }

    // Single source of truth for binding: triggers when dependencies change
    LaunchedEffect(lensFacing, exposureBoostIndex, previewViewRef, cameraProviderRef) {
        val pv = previewViewRef
        val cp = cameraProviderRef
        if (pv != null && cp != null) {
            bindCamera(pv, cp)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            // Use PERFORMANCE mode (SurfaceView) by default - works seamlessly with hardware rendering
            // Prevents dark/black screen bug common with TextureView on emulators
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also { pv ->
                previewViewRef = pv

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderRef = cameraProvider
                        // Binding is triggered by LaunchedEffect once cameraProviderRef is set
                    } catch (e: Exception) {
                        Log.e("CameraPreviewView", "Failed to get CameraProvider", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        update = { pv ->
            previewViewRef = pv
        },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val view = previewViewRef ?: return@detectTapGestures
                    val factory = view.meteringPointFactory
                    val point = factory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    controller.camera?.cameraControl?.startFocusAndMetering(action)
                }
            }
    )
}
