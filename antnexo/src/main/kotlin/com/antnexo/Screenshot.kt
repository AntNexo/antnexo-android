package com.antnexo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import com.antnexo.core.fittedSize
import java.io.ByteArrayOutputStream

/** A bitmap as `data:image/jpeg;base64,…`, scaled to fit max × max, quality 70; null when it can't be encoded. */
internal fun jpegDataUrl(bitmap: Bitmap, max: Int = 1600): String? {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
    return try {
        val size = fittedSize(bitmap.width, bitmap.height, max)
        val scaled = if (size.width == bitmap.width && size.height == bitmap.height) bitmap
        else Bitmap.createScaledBitmap(bitmap, size.width, size.height, true)
        val out = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        if (scaled !== bitmap) scaled.recycle()
        if (!ok) null else "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: RuntimeException) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/**
 * The activity's window as it is now, as a JPEG data URL (main thread; [done] runs on the main thread, with null on any
 * failure). PixelCopy on API 26+ (it includes SurfaceViews: maps, video), View.draw before that.
 */
internal fun captureScreenshot(activity: Activity, done: (String?) -> Unit) {
    val main = Handler(Looper.getMainLooper())
    try {
        val decor = activity.window?.decorView
        if (decor == null || decor.width <= 0 || decor.height <= 0) return done(null)
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= 26) {
            PixelCopy.request(activity.window, bitmap, { result ->
                val url = if (result == PixelCopy.SUCCESS) jpegDataUrl(bitmap) else null
                bitmap.recycle()
                done(url)
            }, main)
        } else {
            decor.draw(Canvas(bitmap))
            val url = jpegDataUrl(bitmap)
            bitmap.recycle()
            done(url)
        }
    } catch (e: RuntimeException) {
        done(null)
    } catch (e: OutOfMemoryError) {
        done(null)
    }
}
