package com.example

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.security.AntiFakeLocationDetector
import com.example.security.SecurityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("KolekPro Cam", appName)
  }

  @Test
  fun `anti fake location detector identifies valid gps location`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val location = Location("gps").apply {
      latitude = -6.175392
      longitude = 106.827153
      altitude = 12.0
      accuracy = 5.0f
      time = System.currentTimeMillis()
    }

    val result = AntiFakeLocationDetector.analyzeLocation(context, location)
    assertTrue(result.isAuthentic)
    assertFalse(result.isMockDetected)
    assertNotNull(result.integrityHash)
    assertNotNull(result.signatureToken)
  }

  @Test
  fun `iphone photonic engine processes bitmap with apple color science`() {
    val src = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
    val processed = com.example.camera.IPhonePhotonicEngine.processBitmap(
      src,
      com.example.camera.PhotoQualityStyle.SMART_HDR_ULTRA
    )
    assertNotNull(processed)
    assertEquals(100, processed.width)
    assertEquals(100, processed.height)
  }
}
