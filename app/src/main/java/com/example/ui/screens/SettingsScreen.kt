package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.config.AppConfig
import com.example.data.AppSettings
import com.example.sync.SpreadsheetSyncManager
import com.example.sync.SyncResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    var spreadsheetUrlInput by remember(settings.spreadsheetUrl) {
        mutableStateOf(settings.spreadsheetUrl)
    }
    var tagInput by remember(settings.companyTag) {
        mutableStateOf(settings.companyTag)
    }
    var qualitySlider by remember(settings.compressionQuality) {
        mutableFloatStateOf(settings.compressionQuality.toFloat())
    }
    var resolutionPreset by remember(settings.resolutionPreset) {
        mutableStateOf(settings.resolutionPreset)
    }
    var autoSyncToggle by remember(settings.autoSyncOnSave) {
        mutableStateOf(settings.autoSyncOnSave)
    }
    var includeThumbToggle by remember(settings.includeThumbnailInSync) {
        mutableStateOf(settings.includeThumbnailInSync)
    }
    var antiFakeToggle by remember(settings.antiFakeEnforced) {
        mutableStateOf(settings.antiFakeEnforced)
    }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<SyncResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Integrasi Spreadsheet & Pengaturan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= 1. GOOGLE SPREADSHEET INTEGRATION =================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = Color(0xFF0D9488),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Integrasi Google Spreadsheet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Setiap foto yang diambil akan secara otomatis mengirim koordinat, waktu, alamat, status anti-fake, dan data ke baris Google Sheets Anda.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val isConfigured = AppConfig.isSpreadsheetConfigured()
                    val activeUrl = if (isConfigured) AppConfig.SPREADSHEET_WEBAPP_URL else settings.spreadsheetUrl

                    // Status Endpoint In-Code Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Endpoint di Code (AppConfig.kt):",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isConfigured) Color(0xFF065F46) else Color(0xFF78350F))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isConfigured) "✓ TERKONFIGURASI DI CODE" else "BELUM DIISI DI CODE",
                                        color = if (isConfigured) Color(0xFF6EE7B7) else Color(0xFFFDE68A),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (activeUrl.isNotBlank()) activeUrl else "https://script.google.com/macros/s/YOUR_APPS_SCRIPT_ID_HERE/exec",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "💡 Tautan Web App disetel langsung di dalam file kode sumber: app/src/main/java/com/example/config/AppConfig.kt",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Connection Button for In-Code Endpoint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                isTestingConnection = true
                                testResult = null
                                val targetUrl = if (isConfigured) AppConfig.SPREADSHEET_WEBAPP_URL else activeUrl
                                viewModel.testSpreadsheetConnection(targetUrl) { res ->
                                    isTestingConnection = false
                                    testResult = res
                                }
                            },
                            enabled = !isTestingConnection && activeUrl.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_connection"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uji Koneksi Endpoint Code", fontSize = 13.sp)
                            }
                        }
                    }

                    // Test Result Feedback
                    testResult?.let { res ->
                        Spacer(modifier = Modifier.height(8.dp))
                        when (res) {
                            is SyncResult.Success -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFD1FAE5))
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF065F46), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(res.message, color = Color(0xFF065F46), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is SyncResult.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFEE2E2))
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFF991B1B), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(res.errorMessage, color = Color(0xFF991B1B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Toggle: Auto sync on save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kirim Otomatis Saat Foto Disimpan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Langsung kirim baris ke spreadsheet tanpa perlu klik manual", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoSyncToggle,
                            onCheckedChange = {
                                autoSyncToggle = it
                                viewModel.updateSettings(settings.copy(autoSyncOnSave = it))
                            }
                        )
                    }
                }
            }

            // ================= 2. PANDUAN & TEMPLATE APPS SCRIPT =================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Template Skrip Google Apps Script",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gunakan skrip siap pakai ini di Google Sheets Anda agar kolom terisi otomatis dan rapi:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "// 1. Buka Google Sheets > Ekstensi > Apps Script\n// 2. Tempel kode doPost(e) ini\n// 3. Terapkan (Deploy) sebagai Web App > Access: Anyone",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val script = SpreadsheetSyncManager.getGoogleAppsScriptTemplate()
                            clipboardManager.setPrimaryClip(
                                ClipData.newPlainText("KolekPro Google Apps Script", script)
                            )
                            Toast.makeText(context, "Skrip Google Apps Script berhasil disalin!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_copy_apps_script"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Seluruh Skrip Google Sheets (1-Klik)", fontSize = 12.5.sp)
                    }
                }
            }

            // ================= 3. PEMETAAN KOLOM OTOMATIS =================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pemetaan Kolom Otomatis (Sheet Mapping)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Urutan kolom otomatis yang masuk ke lembar kerja Google Sheets:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val columns = listOf(
                        "Kolom A" to "Waktu & Jam (Timestamp)",
                        "Kolom B" to "Tanggal Lengkap",
                        "Kolom C" to "Label / Tag Unit (${settings.companyTag})",
                        "Kolom D" to "Alamat Lengkap Geocoded",
                        "Kolom E" to "Latitude GPS",
                        "Kolom F" to "Longitude GPS",
                        "Kolom G" to "Status Keaslian (Asli / Fake GPS)",
                        "Kolom H" to "Hash Integritas (Anti-Tamper SHA-256)",
                        "Kolom I" to "Catatan / Keterangan Kunjungan",
                        "Kolom J" to "Ringkasan Ukuran Foto & Status"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        columns.forEach { (col, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(col, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                Text(desc, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // ================= 4. KOMPRESI OTOMATIS & KUALITAS GAMBAR =================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fitur Kompresi Gambar Otomatis",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Menjaga kualitas ketajaman foto tetap optimal sekaligus menghemat kuota saat sinkronisasi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Preset Resolusi:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1080p" to "Full HD (1080p)", "720p" to "Ringan (720p)", "ORIGINAL" to "Asli (Maks)").forEach { (key, label) ->
                            FilterChip(
                                selected = resolutionPreset == key,
                                onClick = {
                                    resolutionPreset = key
                                    viewModel.updateSettings(settings.copy(resolutionPreset = key))
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kualitas Kompresi JPEG:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${qualitySlider.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Slider(
                        value = qualitySlider,
                        onValueChange = {
                            qualitySlider = it
                            viewModel.updateSettings(settings.copy(compressionQuality = it.toInt()))
                        },
                        valueRange = 40f..100f,
                        steps = 5
                    )
                }
            }

            // ================= 5. KEAMANAN & ANTI FAKE GPS =================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Anti Fake GPS & Enkripsi Data",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mendeteksi aplikasi fake GPS, mock provider, dan menyematkan token verifikasi SHA-256 pada setiap foto.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tegakkan Deteksi Anti Fake GPS", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Sematkan label peringatan merah jika lokasi dimanipulasi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = antiFakeToggle,
                            onCheckedChange = {
                                antiFakeToggle = it
                                viewModel.updateSettings(settings.copy(antiFakeEnforced = it))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Unit Label
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = {
                            tagInput = it
                            viewModel.updateCompanyTag(it)
                        },
                        label = { Text("Label Unit / Nama Usaha (Watermark)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
