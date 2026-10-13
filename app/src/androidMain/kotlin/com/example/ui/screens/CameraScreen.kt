package com.example.ui.screens

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.ui.components.CameraPreviewView
import com.example.ui.components.WatermarkOverlayView
import com.example.watermark.WatermarkStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    onNavigateToGallery: () -> Unit
) {
    val locationData by viewModel.locationData.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val capturedResult by viewModel.capturedResult.collectAsState()
    val lensFacing by viewModel.lensFacing.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val currentMapThumbnail by viewModel.currentMapThumbnail.collectAsState()
    val photoQualityStyle by viewModel.photoQualityStyle.collectAsState()

    var noteText by remember { mutableStateOf("") }
    var showTagDialog by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        CameraPreviewView(
            lensFacing = lensFacing,
            flashMode = flashMode,
            onControllerReady = { controller ->
                viewModel.setCameraController(controller)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.cycleFlashMode() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x660F172A))
                    .testTag("btn_flash_toggle")
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash Mode",
                    tint = Color.White
                )
            }

            // Quality Style Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x880F172A))
                    .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(20.dp))
                    .clickable { viewModel.cyclePhotoQualityStyle() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("btn_quality_style")
            ) {
                Text(
                    text = photoQualityStyle.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                IconButton(
                    onClick = { viewModel.toggleLensFacing() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .testTag("btn_switch_camera")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Controls with Watermark Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            WatermarkOverlayView(
                locationData = locationData,
                tagLabel = settings.companyTag,
                mapBitmap = currentMapThumbnail,
                watermarkStyle = WatermarkStyle.fromString(settings.watermarkStyle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTagDialog = true }
                    .testTag("live_watermark_card")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(
                    onClick = onNavigateToGallery,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .testTag("btn_quick_gallery")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Buka Galeri",
                        tint = Color.White
                    )
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

                // Watermark Style Picker Button (Right of Shutter)
                IconButton(
                    onClick = { showStylePicker = true },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .border(1.dp, Color(0x6638BDF8), CircleShape)
                        .testTag("btn_watermark_style_picker")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Pilih Gaya Watermark",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }
        }

        // Preview Captured Dialog
        if (capturedResult != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPreview() },
                title = { Text("Pratinjau Foto Watermark", color = Color.White) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = capturedResult!!.bitmap.asImageBitmap(),
                            contentDescription = "Foto Watermark",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("Catatan Kunjungan (Opsional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveToGalleryAndLocal(noteText)
                            noteText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.testTag("btn_save_photo")
                    ) {
                        Text("Simpan & Sinkron")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissPreview() },
                        modifier = Modifier.testTag("btn_discard_photo")
                    ) {
                        Text("Ulangi", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }

        // Quick Tag Edit Dialog when tapping Watermark Card
        if (showTagDialog) {
            var tempTag by remember { mutableStateOf(settings.companyTag) }
            AlertDialog(
                onDismissRequest = { showTagDialog = false },
                title = { Text("Ubah Label Tag / Unit", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = tempTag,
                        onValueChange = { tempTag = it },
                        label = { Text("Nama Tag / Unit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_quick_tag")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateSettings(settings.copy(companyTag = tempTag.trim().ifEmpty { "KOLEKPRO" }))
                            showTagDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTagDialog = false }) {
                        Text("Batal", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }

        // Watermark Style Selection ModalBottomSheet
        if (showStylePicker) {
            ModalBottomSheet(
                onDismissRequest = { showStylePicker = false },
                containerColor = Color(0xFF1E293B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Pilih Desain Watermark",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    WatermarkStyle.values().forEach { style ->
                        val isSelected = style.name == settings.watermarkStyle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A))
                                .clickable {
                                    viewModel.updateWatermarkStyle(style)
                                    showStylePicker = false
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = style.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when(style) {
                                        WatermarkStyle.MODERN_DARK_BOTTOM_LEFT -> "Modern Dark Bottom Left"
                                        WatermarkStyle.MODERN_DARK_BOTTOM_RIGHT -> "Modern Dark Bottom Right"
                                        WatermarkStyle.MODERN_LIGHT_BOTTOM_RIGHT -> "Modern Light Bottom Right"
                                        WatermarkStyle.MODERN_LIGHT_BOTTOM_LEFT -> "Modern Light Bottom Left"
                                        WatermarkStyle.MINIMAL_DARK_TOP_LEFT -> "Minimal Dark Top Left"
                                        WatermarkStyle.MODERN_LIGHT_BOTTOM_CENTER -> "Modern Light Bottom Center"
                                        WatermarkStyle.VERTICAL_DARK_LEFT -> "Vertical Dark Left"
                                        WatermarkStyle.ROUNDED_DARK_BOTTOM_LEFT -> "Rounded Dark Bottom Left"
                                    },
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Dipilih",
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
