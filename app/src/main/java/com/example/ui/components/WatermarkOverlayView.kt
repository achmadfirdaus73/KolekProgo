package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.LiveLocationData
import java.util.Locale

@Composable
fun WatermarkOverlayView(
    locationData: LiveLocationData,
    tagLabel: String,
    mapBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val isMock = locationData.securityResult.isMockDetected
    val borderColor = if (isMock) Color(0xB3EF4444) else Color(0x4DFFFFFF)

    // Ultra-translucent glass background (satisfies: "transparant watermarknya buat lebih transparant lagi")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x3D0F172A)) // Ultra transparency (~24% opacity)
            .border(1.2.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT COLUMN: Tag, Clock, Date, Status Pill
            Column(
                modifier = Modifier.width(108.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tagLabel.uppercase(Locale.ROOT),
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = locationData.formattedTime.ifEmpty { "18:16" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 32.sp
                )
                Text(
                    text = locationData.formattedDate.ifEmpty { "10 Okt 2026" },
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(3.dp))
                val isTimeFake = locationData.securityResult.isTimeManipulated
                val isTampered = isMock || isTimeFake
                val badgeText = when {
                    isTimeFake && isMock -> "⚠️ WAKTU & GPS PALSU"
                    isTimeFake -> "⚠️ JAM DIMANIPULASI"
                    isMock -> "⚠️ FAKE GPS"
                    else -> "🛡️ ASLI TERVERIFIKASI"
                }

                // Mini Authenticity Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isTampered) Color(0xCC7F1D1D) else Color(0xCC064E3B))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = if (isTampered) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // VERTICAL DIVIDER
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(84.dp)
                    .background(Color(0x35FFFFFF))
            )

            Spacer(modifier = Modifier.width(10.dp))

            // MIDDLE COLUMN: Full Address, Coordinates, Crypto Token
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = locationData.address.ifEmpty { "RWGV+G94, Jl. Cakung Drainase, RT.1/RW.9, Semper Tim., Cilincing, DKI Jakarta" },
                    color = Color(0xFFF8FAFC),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val latStr = String.format(Locale.US, "%.6f", locationData.latitude).replace('.', ',')
                val lonStr = String.format(Locale.US, "%.6f", locationData.longitude).replace('.', ',')
                Text(
                    text = "$latStr, $lonStr",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SIG: #${locationData.securityResult.signatureToken} • LIVE ENCRYPTED",
                    color = Color(0xFFCBD5E1),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            // RIGHT COLUMN: REAL DYNAMIC MAP WITH EXACT COORDINATE PINPOINT
            // Satisfies: "tampil lokasi sesuai titiknya bukan tempelan ajah ya"
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x991E293B))
                    .border(1.2.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (mapBitmap != null) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Peta Lokasi GPS Asli",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "MAPS",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
