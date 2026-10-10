package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.location.LiveLocationData
import java.util.Locale

@Composable
fun WatermarkOverlayView(
    locationData: LiveLocationData,
    tagLabel: String,
    modifier: Modifier = Modifier
) {
    val isMock = locationData.securityResult.isMockDetected
    val borderColor = if (isMock) Color(0xFFEF4444) else Color(0x66FFFFFF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xD90F172A)) // Dark slate translucent glass
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
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
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(3.dp))
                // Mini Authenticity Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isMock) Color(0xFF7F1D1D) else Color(0xFF064E3B))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isMock) "⚠️ FAKE GPS" else "🛡️ ASLI VERIF",
                        color = if (isMock) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // VERTICAL DIVIDER
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(84.dp)
                    .background(Color(0x40FFFFFF))
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
                    color = Color(0xFF94A3B8),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            // RIGHT COLUMN: 3D MAP BADGE
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x20000000), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_badge_3d_maps),
                    contentDescription = "3D Maps Pin",
                    modifier = Modifier.size(74.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
