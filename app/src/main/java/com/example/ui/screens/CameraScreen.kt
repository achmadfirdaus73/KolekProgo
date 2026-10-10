package com.example.ui.screens

import androidx.camera.core.CameraSelector
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

    val isMock = locationData.securityResult.isMockDetected

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_screen")
    ) {
        // 1. CAMERAX PREVIEW
        CameraPreviewView(
            lensFacing = lensFacing,
            flashMode = flashMode,
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
                val gridColor = Color(0x40FFFFFF)

                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(stepX, 0f), end = androidx.compose.ui.geometry.Offset(stepX, size.height), strokeWidth = 1f)
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(stepX * 2, 0f), end = androidx.compose.ui.geometry.Offset(stepX * 2, size.height), strokeWidth = 1f)
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, stepY), end = androidx.compose.ui.geometry.Offset(size.width, stepY), strokeWidth = 1f)
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, stepY * 2), end = androidx.compose.ui.geometry.Offset(size.width, stepY * 2), strokeWidth = 1f)
            }
        }

        // 3. TOP CONTROLS & STATUS BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xCC000000), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Security / Anti-Fake Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isMock) Color(0xD9DC2626) else Color(0xD9059669))
                        .border(1.dp, if (isMock) Color(0xFFFCA5A5) else Color(0xFF6EE7B7), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMock) Icons.Default.Warning else Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isMock) "FAKE GPS DETECTED" else "LOKASI ASLI ±${locationData.accuracy.toInt()}m",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Camera Controls (Flash, Grid, Switch)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Flash Mode Toggle
                    IconButton(
                        onClick = { viewModel.cycleFlashMode() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x66000000), CircleShape)
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
                            .background(if (gridEnabled) Color(0x992563EB) else Color(0x66000000), CircleShape)
                            .testTag("btn_grid_toggle")
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = "Grid", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Switch Camera Lens
                    IconButton(
                        onClick = { viewModel.toggleLensFacing() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x66000000), CircleShape)
                            .testTag("btn_switch_camera")
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Ganti Kamera", tint = Color.White, modifier = Modifier.size(18.dp))
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

        // 4. BOTTOM WATERMARK OVERLAY & SHUTTER PANEL
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xEE000000))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Live Real-Time Watermark Overlay (replicating final rendered look)
            WatermarkOverlayView(
                locationData = locationData,
                tagLabel = settings.companyTag,
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
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onNavigateToGallery() }
                        .testTag("btn_shortcut_gallery"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Galeri",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // Quick Tag Edit Dialog
    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Ganti Label Unit / Usaha") },
            text = {
                Column {
                    Text(
                        text = "Label ini akan ditampilkan pada watermark foto dan kolom unit di spreadsheet:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempTag,
                        onValueChange = { tempTag = it },
                        label = { Text("Label Tag (e.g. KOLEKPRO)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_quick_tag")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCompanyTag(tempTag)
                        showTagDialog = false
                    },
                    modifier = Modifier.testTag("btn_save_quick_tag")
                ) {
                    Text("Simpan")
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
