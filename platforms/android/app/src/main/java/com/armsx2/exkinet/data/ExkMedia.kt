package com.armsx2.exkinet.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Portadas y fondos se copian dentro de la app (reducidos): no dependen de que el original siga
 * existiendo y entran en las copias. Los vídeos se guardan como referencia con permiso
 * PERSISTENTE (sobreviven a reinicios), no como URL temporal como en la demo web.
 */
object ExkMedia {
    const val COVER_MAX = 1200
    const val BACKGROUND_MAX = 2560

    private fun folderFor(context: Context, key: String): File =
        File(ExkStore.mediaDir(context), key.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)).apply { mkdirs() }

    fun importImage(context: Context, uri: Uri, key: String, kind: String, maxSide: Int): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La imagen no se puede leer" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("La imagen no se puede leer")
        return save(context, scale(bmp, maxSide), key, kind)
    }

    fun importAsset(context: Context, asset: String, key: String, kind: String): String {
        val bmp = context.assets.open(asset).use { BitmapFactory.decodeStream(it) } ?: error(asset)
        return save(context, bmp, key, kind)
    }

    private fun scale(src: Bitmap, maxSide: Int): Bitmap {
        val l = max(src.width, src.height)
        if (l <= maxSide) return src
        val f = maxSide.toFloat() / l
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt(), (src.height * f).roundToInt(), true)
    }

    private fun save(context: Context, bmp: Bitmap, key: String, kind: String): String {
        val dir = folderFor(context, key)
        val f = File(dir, "$kind-${System.currentTimeMillis()}.webp")
        val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        f.outputStream().use { bmp.compress(fmt, 90, it) }
        return f.relativeTo(ExkStore.root(context)).path
    }

    fun delete(context: Context, rel: String?) {
        if (rel == null) return
        val f = ExkStore.file(context, rel)
        if (f.canonicalPath.startsWith(ExkStore.mediaDir(context).canonicalPath)) f.delete()
    }

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "vídeo")

    /** Comprueba que el vídeo tenga pista decodificable antes de activarlo. */
    fun probeVideo(context: Context, uri: Uri): Result<VideoRef> = runCatching {
        val mime = context.contentResolver.getType(uri)
        require(mime == null || mime.startsWith("video/") || mime == "application/octet-stream") { "Ese archivo no es un vídeo" }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            require(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "No contiene una pista de vídeo compatible" }
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            require(w > 0 && h > 0) { "No se pudo leer el tamaño del vídeo" }
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            VideoRef(uri.toString(), displayName(context, uri), w, h)
        } finally { runCatching { r.release() } }
    }

    fun releaseVideo(context: Context, ref: VideoRef?) {
        if (ref == null || ref.uri in ExkStore.videoUrisInUse()) return
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(ref.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** True si el vídeo sigue accesible con el permiso guardado. */
    fun videoReadable(context: Context, ref: VideoRef): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(ref.uri), "r")?.use { true } ?: false
    }.getOrDefault(false)
}
