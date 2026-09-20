package com.leo.checkertic.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * ============================================================================
 *  BACKGROUND IMAGES
 * ============================================================================
 *
 * Storage and decoding for note / category backgrounds.
 *
 * ## Import
 *
 * Images arrive from the system Photo Picker (`PickVisualMedia`), so the app
 * declares no storage permission at all — not `READ_MEDIA_IMAGES`, not
 * `READ_EXTERNAL_STORAGE`. The picker grants a one-shot read URI.
 *
 * That URI is then *copied* into `filesDir/backgrounds/`. Depending on a
 * long-lived external URI would break in at least four ordinary ways: the
 * grant is scoped to the process and dies on reboot, the user can delete the
 * photo from their gallery, cloud-backed picker URIs can require a network
 * round trip to re-resolve, and `takePersistableUriPermission` isn't
 * available for picker results at all. A copy is a few hundred KB and never
 * surprises anyone.
 *
 * ## Decoding
 *
 * Every decode is downsampled to the size it will actually be drawn at. This
 * is the single biggest lever on both jank and memory here: a 12 MP phone
 * photo decoded at full size is ~48 MB of bitmap for a card that occupies
 * 160×84 dp. `inSampleSize` cuts that to a few hundred KB before a single
 * byte reaches the heap.
 *
 * Decoded results are held in an [LruCache] sized as a fraction of the app's
 * heap, keyed by file *and* target bucket, so the notes grid scrolling back
 * and forth never re-decodes.
 *
 * Rolling this by hand rather than pulling in Coil is a deliberate trade: the
 * app loads a handful of local files with no network, no animation and no
 * transformation pipeline, which is a fraction of what an image-loading
 * library exists to do. ~120 lines here avoids a dependency whose surface is
 * an order of magnitude larger than the need.
 */
object ImageStore {

    private const val DIR = "backgrounds"

    /**
     * Decode targets are quantised to a few buckets rather than the exact
     * pixel size of the composable. Without this, a card 161 px wide and one
     * 162 px wide would be separate cache entries and the cache would thrash
     * on every rotation or resize.
     */
    enum class Target(val maxEdgePx: Int) {
        THUMB(400),
        CARD(900),
        FULL(1600)
    }

    private val cache: LruCache<String, Bitmap> by lazy {
        // An eighth of available heap. Enough for a full screen of note
        // backgrounds; small enough that it is never the reason for an OOM.
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        object : LruCache<String, Bitmap>(maxKb / 8) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun fileFor(context: Context, name: String): File = File(dir(context), name)

    /**
     * Copies a picked image into internal storage and returns the reference
     * string to persist (`file:<name>`), or null if the copy failed.
     */
    suspend fun importFromPicker(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = "bg_${UUID.randomUUID()}.jpg"
                val target = fileFor(context, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
                } ?: return@runCatching null
                // Reject anything that isn't a decodable image rather than
                // persisting a reference that will render as a blank card.
                if (!isDecodable(target)) {
                    target.delete()
                    return@runCatching null
                }
                BackgroundRef.file(name)
            }.getOrNull()
        }

    private fun isDecodable(file: File): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }

    /**
     * Decodes a stored background at [target], from cache when possible.
     *
     * Always call from a background dispatcher — it does file I/O and bitmap
     * decoding, neither of which belongs anywhere near a frame deadline.
     */
    suspend fun load(context: Context, name: String, target: Target): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = "$name@${target.name}"
            cache.get(key)?.let { return@withContext it.asImageBitmap() }

            val file = fileFor(context, name)
            if (!file.exists()) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, target.maxEdgePx)
                // RGB_565 halves the memory of a background image and the
                // banding is invisible under a scrim at this size. ARGB_8888
                // would double heap pressure for no perceptible gain.
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                ?: return@withContext null
            cache.put(key, bitmap)
            bitmap.asImageBitmap()
        }

    /**
     * Largest power of two that keeps both edges at or above [maxEdge].
     *
     * Powers of two only, because that is the only thing `inSampleSize`
     * actually honours — any other value is silently rounded down to one.
     */
    internal fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge && h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /** Deletes a stored background and evicts every cached decode of it. */
    suspend fun delete(context: Context, name: String) = withContext(Dispatchers.IO) {
        fileFor(context, name).delete()
        Target.entries.forEach { cache.remove("$name@${it.name}") }
        Unit
    }

    /**
     * Removes background files no longer referenced by any note or category.
     *
     * Called after a restore, which can leave orphans behind when the backup
     * being restored referenced fewer images than the live data did.
     */
    suspend fun pruneUnreferenced(context: Context, referenced: Set<String>) =
        withContext(Dispatchers.IO) {
            dir(context).listFiles()?.forEach { file ->
                if (file.name !in referenced) {
                    file.delete()
                    Target.entries.forEach { cache.remove("${file.name}@${it.name}") }
                }
            }
            Unit
        }

    fun clearMemoryCache() = cache.evictAll()

    private const val DEFAULT_BUFFER = 64 * 1024
}
