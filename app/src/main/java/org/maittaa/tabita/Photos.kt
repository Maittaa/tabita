package org.maittaa.tabita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object Photos {
    private const val MAX_SIDE = 1600

    /** Копирует фото в память приложения (уменьшенное, с учётом поворота). Возвращает имя файла. */
    suspend fun import(ctx: Context, dir: File, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                val w = info.size.width; val h = info.size.height
                val scale = maxOf(w, h).toFloat() / MAX_SIDE
                if (scale > 1f) dec.setTargetSize((w / scale).toInt(), (h / scale).toInt())
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val name = UUID.randomUUID().toString().take(12) + ".jpg"
            File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        }.getOrNull()
    }

    /**
     * 5.45: когда снято фото — EXIF DateTimeOriginal/DateTime оригинала (часовой пояс телефона).
     * При сохранении в приложение снимок пересжимается и EXIF теряется, поэтому читаем до импорта.
     */
    suspend fun takenAt(ctx: Context, uri: Uri): Long? = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                val ex = android.media.ExifInterface(s)
                val v = ex.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL) ?: ex.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                v?.let { parseExif(it) }
            }
        }.getOrNull()
    }

    /** «2026:11:22 13:05:41» → мс в часовом поясе телефона. */
    fun parseExif(v: String): Long? = runCatching {
        java.time.LocalDateTime.parse(v.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    fun decode(file: File, maxSide: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()
}

@Composable
fun PhotoImage(file: File, maxSide: Int, modifier: Modifier, crop: Boolean = true) {
    val img: ImageBitmap? by produceState<ImageBitmap?>(null, file.path, maxSide) {
        value = withContext(Dispatchers.IO) { Photos.decode(file, maxSide)?.asImageBitmap() }
    }
    val i = img
    if (i != null) {
        Image(i, null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
