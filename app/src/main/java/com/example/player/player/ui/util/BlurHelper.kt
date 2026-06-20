package com.example.player.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.InputStream
import java.lang.ref.WeakReference

object BlurHelper {

    private const val TAG = "BlurHelper"
    private const val MAX_CACHE_SIZE = 30

    private val blurCache = LinkedHashMap<String, WeakReference<Bitmap>>(16, 0.75f, true)
    private val cacheLock = Any()

    private val imageBitmapCache = LinkedHashMap<String, WeakReference<ImageBitmap>>(16, 0.75f, true)
    private val imageBitmapLock = Any()

    fun getCachedImageBitmap(uri: String?, targetSize: Int = 200, blurPixels: Int = 16): ImageBitmap? {
        if (uri == null) return null
        val cacheKey = "${uri}_${targetSize}_${blurPixels}"

        synchronized(imageBitmapLock) {
            val cachedRef = imageBitmapCache[cacheKey]
            val cached = cachedRef?.get()
            if (cached != null) {

                try {

                    synchronized(cacheLock) {
                        val bitmapRef = blurCache[cacheKey]
                        val bitmap = bitmapRef?.get()
                        if (bitmap != null && !bitmap.isRecycled) {
                            Log.d(TAG, "ImageBitmap cache HIT for $uri")
                            return cached
                        }
                    }
                } catch (_: Exception) {}
            }

            if (cachedRef != null) {
                imageBitmapCache.remove(cacheKey)
            }
        }
        return null
    }

    suspend fun loadBlurredImageBitmap(
        context: Context,
        uri: String?,
        targetSize: Int = 200,
        blurPixels: Int = 16
    ): ImageBitmap? {
        if (uri == null) return null

        val cached = getCachedImageBitmap(uri, targetSize, blurPixels)
        if (cached != null) return cached

        val bitmap = loadBlurredBitmap(context, uri, targetSize, blurPixels) ?: return null

        val imageBitmap = bitmap.asImageBitmap()

        val cacheKey = "${uri}_${targetSize}_${blurPixels}"
        synchronized(imageBitmapLock) {
            imageBitmapCache[cacheKey] = WeakReference(imageBitmap)

            if (imageBitmapCache.size > MAX_CACHE_SIZE) {
                val iter = imageBitmapCache.entries.iterator()
                while (iter.hasNext() && imageBitmapCache.size > MAX_CACHE_SIZE) {
                    val entry = iter.next()
                    if (entry.value.get() == null) {
                        iter.remove()
                    }
                }
                while (imageBitmapCache.size > MAX_CACHE_SIZE) {
                    val iter2 = imageBitmapCache.entries.iterator()
                    if (iter2.hasNext()) {
                        iter2.next()
                        iter2.remove()
                    } else break
                }
            }
        }

        return imageBitmap
    }

    suspend fun loadBlurredBitmap(
        context: Context,
        uri: String?,
        targetSize: Int = 400,
        blurPixels: Int = 32
    ): Bitmap? {
        if (uri == null) {
            Log.w(TAG, "URI is null")
            return null
        }

        val cacheKey = "${uri}_${targetSize}_${blurPixels}"

        synchronized(cacheLock) {
            val cachedRef = blurCache[cacheKey]
            val cached = cachedRef?.get()
            if (cached != null && !cached.isRecycled) {
                Log.d(TAG, "Bitmap cache HIT for $uri (size=$targetSize)")
                return cached
            }

            if (cachedRef != null) {
                blurCache.remove(cacheKey)
            }
        }

        return try {
            Log.d(TAG, "Loading for blur: uri=$uri targetSize=$targetSize blurPixels=$blurPixels")
            val sourceBitmap = loadBitmapFromUri(context, uri)
                ?: run {
                    Log.w(TAG, "Failed to load bitmap from URI: $uri")
                    return null
                }

            Log.d(TAG, "Source bitmap: ${sourceBitmap.width}x${sourceBitmap.height}")

            val blurred = fastBlur(sourceBitmap, targetSize, blurPixels)

            if (sourceBitmap !== blurred && !sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }

            synchronized(cacheLock) {
                blurCache[cacheKey] = WeakReference(blurred)

                if (blurCache.size > MAX_CACHE_SIZE) {
                    val iter = blurCache.entries.iterator()
                    while (iter.hasNext() && blurCache.size > MAX_CACHE_SIZE) {
                        val entry = iter.next()
                        val ref = entry.value.get()
                        if (ref == null || ref.isRecycled) {
                            iter.remove()
                        }
                    }

                    while (blurCache.size > MAX_CACHE_SIZE) {
                        val iter2 = blurCache.entries.iterator()
                        if (iter2.hasNext()) {
                            iter2.next()
                            iter2.remove()
                        } else break
                    }
                }
            }

            Log.d(TAG, "Blurred result: ${blurred.width}x${blurred.height}")
            blurred
        } catch (e: Exception) {
            Log.e(TAG, "Blur failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Blur OOM: ${e.message}")
            clearCache()
            null
        }
    }

    fun isCached(uri: String?, targetSize: Int = 200, blurPixels: Int = 16): Boolean {
        if (uri == null) return false
        val cacheKey = "${uri}_${targetSize}_${blurPixels}"
        synchronized(imageBitmapLock) {
            val cachedRef = imageBitmapCache[cacheKey]
            val cached = cachedRef?.get()
            if (cached != null) {
                synchronized(cacheLock) {
                    val bitmapRef = blurCache[cacheKey]
                    val bitmap = bitmapRef?.get()
                    if (bitmap != null && !bitmap.isRecycled) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                    ?: run {
                        Log.w(TAG, "ContentResolver returned null stream for $uriString")
                        return null
                    }

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                val maxSize = 256
                options.inJustDecodeBounds = false
                options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
                options.inPreferredConfig = Bitmap.Config.RGB_565

                try {
                    inputStream = context.contentResolver.openInputStream(uri) ?: return null
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream.close()

                    if (bitmap == null) {
                        Log.w(TAG, "BitmapFactory.decodeStream returned null for $uriString")
                    }
                    bitmap
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM loading bitmap, trying with smaller sample size")
                    try {
                        options.inSampleSize = (options.inSampleSize * 2).coerceAtMost(8)
                        inputStream = context.contentResolver.openInputStream(uri) ?: return null
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream.close()
                        bitmap
                    } catch (e2: OutOfMemoryError) {
                        Log.e(TAG, "OOM even with reduced sample size, giving up")
                        null
                    }
                }
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap: ${e.message}")
            null
        }
    }

    private fun fastBlur(source: Bitmap, targetSize: Int, blurPixels: Int): Bitmap {
        val small = Bitmap.createScaledBitmap(source, blurPixels, blurPixels, true)

        var blurred = small
        try {
            for (i in 0 until 3) {
                blurred = simpleBoxBlur(blurred)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Box blur failed, using scaled result: ${e.message}")
            if (blurred !== small && !blurred.isRecycled) {
                blurred.recycle()
            }
            blurred = small
        }

        val result = Bitmap.createScaledBitmap(blurred, targetSize, targetSize, true)

        if (small !== blurred && !small.isRecycled) {
            try { small.recycle() } catch (_: Exception) {}
        }
        if (blurred !== result && !blurred.isRecycled) {
            try { blurred.recycle() } catch (_: Exception) {}
        }

        return result
    }

    private fun simpleBoxBlur(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        val dst = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0; var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val pixel = src[ny * w + nx]
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                        a += (pixel shr 24) and 0xFF
                        count++
                    }
                }
                dst[y * w + x] = (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dst, 0, w, 0, 0, w, h)
        return result
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun clearCache() {
        synchronized(cacheLock) {
            blurCache.clear()
        }
        synchronized(imageBitmapLock) {
            imageBitmapCache.clear()
        }
    }
}
