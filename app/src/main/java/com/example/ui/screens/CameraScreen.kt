package com.example.ui.screens

import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.data.PhotoRecord
import com.example.ui.components.CameraPreviewView
import com.example.ui.components.WatermarkOverlayView

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    locationData: com.example.location.LiveLocationData,
    settings: com.example.data.AppSettings,
    photos: List<PhotoRecord>,
    isCapturing: Boolean,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTagDialog by remember { mutableStateOf(false) }
    var tempTag by remember { mutableStateOf(settings.companyTag) }

    val lensFacing by viewModel.lensFacing.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val gridEnabled by viewModel.gridEnabled.collectAsState()
    val exposureBoost by viewModel.exposureBoost.collectAsState()
    val currentMapThumbnail by viewModel.currentMapThumbnail.collectAsState()
    val iphoneStyle by viewModel.iphoneStyle.collectAsState()

    val isMock = locationData.securityResult.isMockDetected

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_screen")
    ) {
        // 1. CAMERAX PREVIEW (Using SurfaceView performance mode to eliminate dark/black screen)
        CameraPreviewView(
            lensFacing = lensFacing,
            flashMode = flashMode,
            exposureBoostIndex = exposureBoost,
            onControllerReady = { controller ->
                viewModel.cameraController = controller
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. GRID OVERLAY (IF ENABLED)
        if (gridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stepX = size.width / 3
                val stepY = size.height / 3
                val gridColor = Color(0x35FFFFFF)

                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(stepX, 0f), end = androidx.compose.ui.geometry.Offset(stepX, size.height), strokeWidth = 1f)
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(stepX * 2, 0f), end = androidx.compose.ui.geometry.Offset(stepX * 2, size.height), strokeWidth = 1f)
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, stepY), end = androidx.compose.ui.geometry.Offset(size.width, stepY), strokeWidth = 1f)
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, stepY * 2), end = androidx.compose.ui.geometry.Offset(size.width, stepY * 2), strokeWidth = 1f)
            }
        }

        // 3. TOP CONTROLS & STATUS BAR (Light gradient so camera is bright and clear)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0x55000000), Color.Transparent)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isTimeFake = locationData.securityResult.isTimeManipulated
                val isTampered = isMock || isTimeFake
                val topStatusText = when {
                    isTimeFake && isMock -> "WAKTU & GPS DIMANIPULASI"
                    isTimeFake -> "JAM SISTEM DIMANIPULASI"
                    isMock -> "FAKE GPS TERDETEKSI"
                    else -> "LOKASI ASLI ±${locationData.accuracy.toInt()}m"
                }

                // Security / Anti-Fake Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isTampered) Color(0xD9DC2626) else Color(0xD9059669))
                        .border(1.dp, if (isTampered) Color(0xFFFCA5A5) else Color(0xFF6EE7B7), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isTampered) Icons.Default.Warning else Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = topStatusText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Camera Controls (Exposure Brightness, Flash, Grid, Switch)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Brightness / Exposure Boost Toggle (+0, +2, +4)
                    IconButton(
                        onClick = { viewModel.cycleExposureBoost() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (exposureBoost > 0) Color(0xCCF59E0B) else Color(0x55000000),
                                CircleShape
                            )
                            .testTag("btn_exposure_boost")
                    ) {
                        Icon(
                            Icons.Default.Brightness6,
                            contentDescription = "Pencerah Kamera",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Flash Mode Toggle
                    IconButton(
                        onClick = { viewModel.cycleFlashMode() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x55000000), CircleShape)
                            .testTag("btn_flash_toggle")
                    ) {
                        val flashIcon = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            else -> Icons.Default.FlashOff
                        }
                        Icon(flashIcon, contentDescription = "Flash", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Grid Toggle
                    IconButton(
                        onClick = { viewModel.toggleGrid() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (gridEnabled) Color(0x992563EB) else Color(0x55000000), CircleShape)
                            .testTag("btn_grid_toggle")
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = "Grid", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Switch Camera Lens
                    IconButton(
                        onClick = { viewModel.toggleLensFacing() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x55000000), CircleShape)
                            .testTag("btn_switch_camera")
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Ganti Kamera", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Smart HDR & Photo Clarity Quality Selector (no iPhone text)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xD90F172A))
                        .border(
                            1.dp,
                            if (iphoneStyle != com.example.camera.PhotoQualityStyle.OFF) Color(0x8838BDF8) else Color(0x44FFFFFF),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.cyclePhotoQualityStyle() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("btn_photo_quality_toggle")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Tone HDR",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = iphoneStyle.title,
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x3338BDF8))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HDR",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Warning Banner if Fake GPS is detected
            AnimatedVisibility(
                visible = isMock,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xEE7F1D1D))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "PERINGATAN SISTEM: Lokasi tiruan / Fake GPS terdeteksi pada perangkat ini. Foto akan ditandai dengan label ketidakaslian data.",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 4. BOTTOM WATERMARK OVERLAY & SHUTTER PANEL (Subtle gradient, preserving viewfinder brightness)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x44000000))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Live Real-Time Watermark Overlay (Showing real pinpoint map + high transparency)
            WatermarkOverlayView(
                locationData = locationData,
                tagLabel = settings.companyTag,
                mapBitmap = currentMapThumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTagDialog = true }
                    .testTag("live_watermark_card")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Bar (Tag selector, Big Shutter, Gallery preview shortcut)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Quick Tag Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x661E293B))
                        .border(1.dp, Color(0x5538BDF8), RoundedCornerShape(20.dp))
                        .clickable {
                            tempTag = settings.companyTag
                            showTagDialog = true
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("btn_change_tag")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Tag",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = settings.companyTag,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                        .border(3.dp, Color.White, CircleShape)
                        .clickable(enabled = !isCapturing) {
                            viewModel.triggerShutter()
                        }
                        .testTag("btn_shutter"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                // Gallery Shortcut Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x661E293B))
                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onNavigateToGallery() }
                        .testTag("btn_quick_gallery"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Buka Riwayat",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // DIALOG QUICK EDIT TAG
    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Ubah Label Watermark") },
            text = {
                Column {
                    Text(
                        "Label ini akan dicetak di sudut watermark foto:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempTag,
                        onValueChange = { tempTag = it },
                        singleLine = true,
                        placeholder = { Text("KOLEKPRO / NAMA TIM") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = tempTag.trim().ifEmpty { "KOLEKPRO" }
                        viewModel.updateCompanyTag(cleaned)
                        showTagDialog = false
                    }
                ) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
