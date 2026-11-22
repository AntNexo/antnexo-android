package com.antnexo

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceContextTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun describesTheAppAndDevice() {
        val c = deviceContext(app)
        assertEquals("Android", c.os)
        assertEquals(Build.VERSION.RELEASE, c.osVersion)
        assertNotNull(c.app)
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        assertEquals(info.versionName, c.appVersion)
        assertNotNull(c.appBuild)
        assertTrue(!c.device.isNullOrEmpty() && c.device!!.length <= 64)
    }

    @Test
    fun localeIsBcp47FromTheAppsConfiguration() {
        val cfg = Configuration(app.resources.configuration)
        cfg.setLocale(Locale("en", "CZ"))
        val c = deviceContext(app.createConfigurationContext(cfg))
        assertEquals("en-CZ", c.locale)
    }

    @Test
    fun jpegDataUrlScalesDown() {
        val b = Bitmap.createBitmap(3200, 800, Bitmap.Config.ARGB_8888)
        val url = jpegDataUrl(b)!!
        assertTrue(url.startsWith("data:image/jpeg;base64,"))
        assertFalse(url.contains("\n"))
        val bytes = Base64.decode(url.removePrefix("data:image/jpeg;base64,"), Base64.DEFAULT)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        assertEquals(1600, opts.outWidth)
        assertEquals(400, opts.outHeight)
    }

    @Test
    fun jpegDataUrlOfARecycledBitmapIsNull() {
        val b = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        b.recycle()
        assertNull(jpegDataUrl(b))
    }

    @Test
    fun seenStoreKeepsWorkspacesApart() {
        val s = PrefsSeenStore(app)
        assertNull(s.get("a"))
        s.set("2026-11-01T00:00:00Z", "a")
        s.set("2026-11-02T00:00:00Z", "b")
        assertEquals("2026-11-01T00:00:00Z", PrefsSeenStore(app).get("a"))
        assertEquals("2026-11-02T00:00:00Z", s.get("b"))
    }
}
