package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.watermark.WatermarkStyle
import java.util.Locale

@Composable
fun WatermarkOverlayView(
    locationData: LiveLocationData,
    tagLabel: String,
    mapBitmap: Bitmap?,
    watermarkStyle: WatermarkStyle = WatermarkStyle.MODERN_DARK_BOTTOM_LEFT,
    modifier: Modifier = Modifier
) {
    val isMock = locationData.securityResult.isMockDetected
    val isTimeFake = locationData.securityResult.isTimeManipulated
    val isTampered = isMock || isTimeFake

    val (bgColor, borderColor, headerColor) = when (watermarkStyle) {
        WatermarkStyle.MODERN_DARK_BOTTOM_LEFT,
        WatermarkStyle.MODERN_DARK_BOTTOM_RIGHT,
        WatermarkStyle.ROUNDED_DARK_BOTTOM_LEFT -> Triple(Color(0xA90F172A), if (isTampered) Color(0xB3EF4444) else Color(0x55FFFFFF), null)
        WatermarkStyle.MODERN_LIGHT_BOTTOM_RIGHT,
        WatermarkStyle.MODERN_LIGHT_BOTTOM_LEFT,
        WatermarkStyle.MODERN_LIGHT_BOTTOM_CENTER -> Triple(Color(0xA9FFFFFF), Color(0x550F172A), null)
        WatermarkStyle.MINIMAL_DARK_TOP_LEFT,
        WatermarkStyle.VERTICAL_DARK_LEFT -> Triple(Color(0xA90F172A), Color(0x55FFFFFF), null)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .background(bgColor)
    ) {
        if ((watermarkStyle == WatermarkStyle.MODERN_DARK_BOTTOM_LEFT || watermarkStyle == WatermarkStyle.MODERN_DARK_BOTTOM_RIGHT) && mapBitmap != null) {
            Image(
                bitmap = mapBitmap.asImageBitmap(),
                contentDescription = "Peta Background",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xA90F172A))
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (headerColor != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(color = headerColor!!)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "EXECUTIVE AUDIT • ${tagLabel.uppercase(Locale.ROOT)}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.width(108.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (headerColor == null) {
                        Text(
                            text = tagLabel.uppercase(Locale.ROOT),
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    val badgeText = when {
                        isTimeFake && isMock -> "⚠️ WAKTU & GPS PALSU"
                        isTimeFake -> "⚠️ JAM DIMANIPULASI"
                        isMock -> "⚠️ FAKE GPS"
                        else -> "🛡️ ASLI TERVERIFIKASI"
                    }

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

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(84.dp)
                        .background(Color(0x35FFFFFF))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = locationData.address.ifEmpty { "Jl. Raya No. 1, Jakarta, Indonesia" },
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
                    val stampText = "SIG: #${locationData.securityResult.signatureToken} • LIVE ENCRYPTED"
                    Text(
                        text = stampText,
                        color = Color(0xFFCBD5E1),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
