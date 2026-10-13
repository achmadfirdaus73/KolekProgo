package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.data.PhotoRecord
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val photos by viewModel.allPhotos.collectAsState()
    var selectedPhoto by remember { mutableStateOf<PhotoRecord?>(null) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Galeri Verifikasi (${photos.size})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_gallery_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Kamera",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (photos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Belum ada foto",
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Belum ada foto verifikasi tersimpan",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ambil foto dengan watermark untuk melihat hasil di sini",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPhoto = photo }
                                .testTag("photo_item_${photo.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                val file = File(photo.filePath)
                                val bitmap = remember(photo.filePath) {
                                    if (file.exists()) {
                                        try {
                                            BitmapFactory.decodeFile(file.absolutePath)
                                        } catch (_: Exception) { null }
                                    } else null
                                }

                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Foto ${photo.tagLabel}",
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF334155)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = "Gambar tidak tersedia",
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = photo.tagLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )

                                        // Badge sync
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (photo.isSynced) Color(0xFF065F46) else Color(0xFF854D0E)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (photo.isSynced) "TERKIRIM" else "BELUM SYNC",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "${photo.formattedDate} • ${photo.formattedTime}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )

                                    if (photo.address.isNotEmpty()) {
                                        Text(
                                            text = photo.address,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1
                                        )
                                    }

                                    if (photo.note.isNotEmpty()) {
                                        Text(
                                            text = "Catatan: ${photo.note}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8),
                                            maxLines = 1
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        isSyncing = true
                                        viewModel.manualSyncPhoto(photo) { success, msg ->
                                            isSyncing = false
                                            syncStatusMessage = msg
                                        }
                                    },
                                    modifier = Modifier.testTag("btn_sync_${photo.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Sync to Google Sheets",
                                        tint = if (photo.isSynced) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sync message toast snackbar
            if (syncStatusMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { syncStatusMessage = null }) {
                            Text("Tutup", color = Color(0xFF38BDF8))
                        }
                    }
                ) {
                    Text(syncStatusMessage ?: "")
                }
            }

            // Detail Photo Dialog
            if (selectedPhoto != null) {
                val photo = selectedPhoto!!
                val file = File(photo.filePath)
                val fullBitmap = remember(photo.filePath) {
                    if (file.exists()) {
                        try {
                            BitmapFactory.decodeFile(file.absolutePath)
                        } catch (_: Exception) { null }
                    } else null
                }

                AlertDialog(
                    onDismissRequest = { selectedPhoto = null },
                    title = {
                        Text(
                            "${photo.tagLabel} (${photo.formattedDate})",
                            color = Color.White
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (fullBitmap != null) {
                                Image(
                                    bitmap = fullBitmap.asImageBitmap(),
                                    contentDescription = "Detail Foto",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Koordinat: ${photo.latitude}, ${photo.longitude}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                "Lokasi: ${photo.address}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                "Token: ${photo.signatureToken} • Hash: ${photo.integrityHash.take(12)}...",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                            if (photo.note.isNotEmpty()) {
                                Text(
                                    "Catatan: ${photo.note}",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.manualSyncPhoto(photo) { _, msg ->
                                    syncStatusMessage = msg
                                }
                                selectedPhoto = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Kirim ke Google Sheets")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.deletePhoto(photo.id)
                                selectedPhoto = null
                            }
                        ) {
                            Text("Hapus", color = Color(0xFFEF4444))
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }
        }
    }
}
