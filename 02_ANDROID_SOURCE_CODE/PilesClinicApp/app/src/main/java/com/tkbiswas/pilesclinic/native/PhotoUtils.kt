package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Shared image helpers for the photo screens: downscale + JPEG-compress a picked
 * gallery image into a small data-URL, and decode a data-URL back to a bitmap.
 * Kept small so both Patient Photo and User Photo use identical encoding.
 */
object PhotoUtils {

    /** TK-APPROVED (2026-07-26): was maxSide=400, quality=70 (~22 KB), which
     *  looked soft on the Patient Card and on print. Raised to 600 / 85 --
     *  measured at roughly 54 KB as a JPEG, about 72 KB once stored as a
     *  data-URL, so it stays far below TK's 100-150 KB ceiling and costs only
     *  about 2.5x the old size, not the 3-4x that was feared. Every OLD photo
     *  is untouched -- this only affects photos taken from now on. Callers,
     *  screens, shapes and layout are all unchanged; the two numbers are the
     *  only difference. PatientPhotoActivity has its own private copy of this
     *  same logic and was updated to match, so both screens stay identical. */
    fun encodeResized(context: Context, uri: Uri, maxSide: Int = 600, quality: Int = 85): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()
            if (original == null) return null
            val scale = maxSide.toFloat() / maxOf(original.width, original.height).toFloat()
            val bmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
                )
            } else original
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decodeDataUrl(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return try {
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return null
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
