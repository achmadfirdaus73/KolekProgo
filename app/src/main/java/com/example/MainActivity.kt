package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.PhotoRecord
import com.example.ui.components.PhotoPreviewDialog
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.StorageHelper
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                MainContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainContent(viewModel: MainViewModel) {
    val context = LocalContext.current

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        permissionsGranted = permissionsMap.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    if (!permissionsGranted) {
        PermissionRequestView(
            onRequestPermissions = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
        return
    }

    val activeTab by viewModel.activeTab.collectAsState()
    val locationData by viewModel.locationData.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val photos by viewModel.allPhotos.collectAsState()
    val capturedResult by viewModel.capturedResult.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncSuccessMessage by viewModel.syncSuccessMessage.collectAsState()
    val syncErrorMessage by viewModel.syncErrorMessage.collectAsState()
    val isSavedToGallery by viewModel.isSavedToGallery.collectAsState()

    val pendingCount = remember(photos) {
        photos.count { it.syncStatus != PhotoRecord.SYNC_STATUS_SYNCED }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                // Tab 1: Kamera
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    icon = {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Kamera",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Kamera", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_camera")
                )

                // Tab 2: Riwayat Foto
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = Color.Black
                                    ) {
                                        Text("$pendingCount", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Riwayat",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    label = { Text("Riwayat", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_gallery")
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> CameraScreen(
                    viewModel = viewModel,
                    locationData = locationData,
                    settings = settings,
                    photos = photos,
                    isCapturing = isCapturing,
                    onNavigateToGallery = { viewModel.setActiveTab(1) }
                )
                else -> GalleryScreen(
                    viewModel = viewModel,
                    photos = photos
                )
            }

            // Interactive Preview Dialog when a photo is taken
            capturedResult?.let { result ->
                PhotoPreviewDialog(
                    watermarkResult = result,
                    isMockDetected = locationData.securityResult.isMockDetected,
                    addressText = locationData.address,
                    coordinatesText = "${locationData.latitude}, ${locationData.longitude}",
                    isSyncing = isSyncing,
                    syncSuccessMessage = syncSuccessMessage,
                    syncErrorMessage = syncErrorMessage,
                    isSavedToGallery = isSavedToGallery,
                    onRetake = { viewModel.dismissPreview() },
                    onSaveToGallery = { note ->
                        viewModel.saveToGalleryAndLocal(note)
                    },
                    onShare = {
                        val file = File(context.filesDir, "preview_share.jpg")
                        try {
                            file.writeBytes(result.jpegBytes)
                            StorageHelper.shareImage(context, file)
                        } catch (_: Exception) {}
                    },
                    onDismiss = { viewModel.dismissPreview() }
                )
            }
        }
    }
}

@Composable
fun PermissionRequestView(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E293B))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(54.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Izin Kamera & Lokasi GPS Diperlukan",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "KolekPro Cam membutuhkan akses Kamera untuk mengambil foto langsung dan Lokasi GPS berpresisi tinggi untuk menyematkan koordinat serta waktu secara real-time dan mendeteksi fake GPS.",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_grant_permissions"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Izinkan Akses Kamera & GPS", fontWeight = FontWeight.Bold)
            }
        }
    }
}
