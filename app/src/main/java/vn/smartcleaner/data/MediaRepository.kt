package vn.smartcleaner.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.smartcleaner.core.Format
import vn.smartcleaner.core.Grouping
import vn.smartcleaner.core.Hashing

/** Truy cập ảnh/video qua MediaStore (scoped storage, không cần All-files-access). */
class MediaRepository(private val ctx: Context, private val db: AppDb) {
    private val cr get() = ctx.contentResolver

    fun queryMedia(video: Boolean): List<MediaItem> {
        val col = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val proj = mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.RELATIVE_PATH)
        if (video) proj += MediaStore.Video.Media.DURATION
        val out = ArrayList<MediaItem>()
        cr.query(col, proj.toTypedArray(), null, null, "${MediaStore.MediaColumns.DATE_TAKEN} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val taken = c.getLong(5).takeIf { it > 0 } ?: (c.getLong(6) * 1000)
                out += MediaItem(id, ContentUris.withAppendedId(col, id), c.getString(1) ?: "?", c.getLong(2), c.getInt(3), c.getInt(4),
                    taken, c.getString(7) ?: "", c.getString(8) ?: "", video, if (video) c.getLong(9) else 0)
            }
        }
        return out
    }

    fun audioBytes(): Long = sumSize(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
    private fun sumSize(uri: Uri): Long {
        var s = 0L
        cr.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c -> while (c.moveToNext()) s += c.getLong(0) }
        return s
    }

    fun storage(appsBytes: Long): StorageSummary {
        val st = StatFs(Environment.getDataDirectory().path)
        val imgs = queryMedia(false).sumOf { it.size }; val vids = queryMedia(true).sumOf { it.size }
        return StorageSummary(st.totalBytes, st.availableBytes, imgs, vids, audioBytes(), appsBytes)
    }

    /** Ảnh xám thu nhỏ từ thumbnail hệ thống (nhanh, không đọc toàn bộ tệp). */
    private fun gray(uri: Uri, px: Int = 256): Triple<IntArray, Int, Int>? = runCatching {
        val bmp = cr.loadThumbnail(uri, Size(px, px), null)
        val sc = Bitmap.createScaledBitmap(bmp, minOf(bmp.width, px), minOf(bmp.height, px) , true)
        val arr = IntArray(sc.width * sc.height); sc.getPixels(arr, 0, sc.width, 0, 0, sc.width, sc.height)
        Triple(IntArray(arr.size) { Hashing.luma(arr[it]) }, sc.width, sc.height)
    }.getOrNull()

    /** Quét toàn bộ: băm, độ nét, nhóm trùng/tương tự. Có cache Room theo (id, size). */
    suspend fun scan(threshold: Int, onProgress: (Float) -> Unit = {}): ScanResult = withContext(Dispatchers.IO) {
        val photos = queryMedia(false); val videos = queryMedia(true)
        val cache = db.hash().all().associateBy { it.id }
        val fresh = ArrayList<HashEntity>()
        val infos = photos.mapIndexedNotNull { i, m ->
            if (i % 20 == 0) onProgress(i.toFloat() / photos.size.coerceAtLeast(1))
            val c = cache[m.id]?.takeIf { it.size == m.size }
            if (c != null) PhotoInfo(m, c.dHash, c.sharpness, c.sha)
            else gray(m.uri)?.let { (g, w, h) ->
                PhotoInfo(m, Hashing.dHash(g, w, h), Hashing.laplacianVariance(g, w, h)).also { fresh += HashEntity(m.id, m.dateTaken, m.size, it.dHash, it.sharpness, null) }
            }
        }.toMutableList()
        // SHA-256 chỉ cho các ảnh trùng kích thước byte (tránh đọc toàn bộ thư viện)
        val sameSize = infos.groupBy { it.item.size }.values.filter { it.size > 1 }.flatten().map { it.item.id }.toSet()
        for (k in infos.indices) {
            val p = infos[k]
            if (p.item.id in sameSize && p.sha == null) {
                val sha = runCatching { cr.openInputStream(p.item.uri)!!.let(Hashing::sha256) }.getOrNull()
                infos[k] = p.copy(sha = sha); fresh += HashEntity(p.item.id, p.item.dateTaken, p.item.size, p.dHash, p.sharpness, sha)
            }
        }
        if (fresh.isNotEmpty()) db.hash().put(fresh.associateBy { it.id }.values.toList())
        val dups = Grouping.exact(infos) { it.sha }.map { g -> g.sortedBy { it.item.dateTaken } }
        val dupIds = dups.flatten().map { it.item.id }.toSet()
        val similar = Grouping.similar(infos.filter { it.item.id !in dupIds }, { it.dHash }, { it.item.dateTaken }, threshold, windowMs = 10 * 60_000L)
        val shots = photos.filter { it.relPath.contains("Screenshot", true) || it.name.startsWith("Screenshot", true) }
        val shotIds = shots.map { it.id }.toSet()
        val blurry = infos.filter { Hashing.isBlurry(it.sharpness) && it.item.id !in shotIds }
        val cutoff = System.currentTimeMillis() - 90 * Format.DAY
        val oldDl = (photos + videos).filter { it.relPath.startsWith("Download", true) && it.dateTaken < cutoff }
        val large = (photos + videos).filter { it.size >= 50L * 1024 * 1024 }.sortedByDescending { it.size }
        onProgress(1f)
        ScanResult(dups, similar, blurry, shots, oldDl, large, videos.sortedByDescending { it.size }, photos)
    }

    fun exif(uri: Uri): Map<String, String> = runCatching {
        cr.openInputStream(uri)!!.use { s ->
            val e = ExifInterface(s)
            listOfNotNull(
                e.getAttribute(ExifInterface.TAG_MODEL)?.let { "Máy" to it },
                e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { "Chụp lúc" to it },
                e.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "Khẩu độ" to "f/$it" },
                e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "Tốc độ" to "${it}s" },
                e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO" to it },
            ).toMap()
        }
    }.getOrDefault(emptyMap())
}
