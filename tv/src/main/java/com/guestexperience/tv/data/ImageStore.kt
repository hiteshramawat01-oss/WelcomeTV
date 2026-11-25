package com.guestexperience.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Keeps the host's background photo as one downsampled JPEG in app storage (≤ 1920×1080),
 * so a phone-camera photo never costs more than ~8 MB of memory on a 2 GB TV.
 */
class ImageStore(private val context: Context) {
    val photoFile: File get() = File(context.filesDir, "background.jpg")

    fun hasPhoto(): Boolean = photoFile.exists() && photoFile.length() > 0

    suspend fun importPhoto(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sample = 1
            while (max(bounds.outWidth / (sample * 2), bounds.outHeight / (sample * 2)) >= MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext false

            // Cover-fit for a 1920×1080 screen: the shorter side relative to the screen decides.
            val scale = minOf(1f, maxOf(MAX_W / decoded.width, MAX_H / decoded.height))
            val output = if (scale < 1f) {
                decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt())
                    .also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }
            val tmp = File(context.filesDir, "background.tmp")
            tmp.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            output.recycle()
            tmp.renameTo(photoFile)
        } catch (_: Exception) {
            false
        }
    }

    fun deletePhoto() {
        photoFile.delete()
    }

    private companion object {
        const val MAX_W = 1920f
        const val MAX_H = 1080f
        const val MAX_EDGE = 1920
    }
}
