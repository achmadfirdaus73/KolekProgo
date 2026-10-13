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

    var showSettingsSheet by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

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
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
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
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x660F172A))
                        .testTag("btn_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
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
                .padding(bottom = 24.dp, start = 12.dp, end = 12.dp)
        ) {
            WatermarkOverlayView(
                locationData = locationData,
                tagLabel = settings.companyTag,
                mapBitmap = currentMapThumbnail,
                modifier = Modifier.fillMaxWidth().testTag("live_watermark_card")
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

                // Placeholder space for balance
                Spacer(modifier = Modifier.size(50.dp))
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

        // Settings Bottom Sheet
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color(0xFF1E293B)
            ) {
                var currentTag by remember { mutableStateOf(settings.companyTag) }
                var currentUrl by remember { mutableStateOf(settings.spreadsheetUrl) }
                var autoSync by remember { mutableStateOf(settings.autoSyncToSpreadsheet) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Pengaturan KolekPro Cam",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = currentTag,
                        onValueChange = { currentTag = it },
                        label = { Text("Label Tag Perusahaan / Unit") },
                        modifier = Modifier.fillMaxWidth().testTag("input_company_tag")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = currentUrl,
                        onValueChange = { currentUrl = it },
                        label = { Text("URL Web App Google Sheets") },
                        modifier = Modifier.fillMaxWidth().testTag("input_spreadsheet_url"),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Otomatis Sinkronisasi ke Spreadsheet",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Switch(
                            checked = autoSync,
                            onCheckedChange = { autoSync = it },
                            modifier = Modifier.testTag("switch_auto_sync")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            viewModel.updateSettings(
                                settings.copy(
                                    companyTag = currentTag.trim().ifEmpty { "KOLEKPRO" },
                                    spreadsheetUrl = currentUrl.trim(),
                                    autoSyncToSpreadsheet = autoSync
                                )
                            )
                            showSettingsSheet = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_save_settings"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Simpan Pengaturan")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
