package vn.smartcleaner.data

import android.net.Uri

data class MediaItem(
    val id: Long, val uri: Uri, val name: String, val size: Long,
    val width: Int, val height: Int, val dateTaken: Long, val mime: String,
    val relPath: String, val isVideo: Boolean = false, val durationMs: Long = 0,
) { val pixels: Long get() = width.toLong() * height }

data class PhotoInfo(val item: MediaItem, val dHash: Long, val sharpness: Double, val sha: String? = null)

data class StorageSummary(
    val total: Long, val free: Long, val images: Long, val videos: Long, val audio: Long, val apps: Long,
) {
    val used get() = total - free
    val other get() = (used - images - videos - audio - apps).coerceAtLeast(0)
}

data class ScanResult(
    val duplicates: List<List<PhotoInfo>> = emptyList(),
    val similar: List<List<PhotoInfo>> = emptyList(),
    val blurry: List<PhotoInfo> = emptyList(),
    val screenshots: List<MediaItem> = emptyList(),
    val oldDownloads: List<MediaItem> = emptyList(),
    val largeFiles: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val photos: List<MediaItem> = emptyList(),
) {
    /** Dung lượng có thể giải phóng: các bản trùng (giữ 1) + ảnh mờ + ảnh chụp màn hình. */
    val duplicateBytes get() = duplicates.sumOf { g -> g.drop(1).sumOf { it.item.size } }
    val similarBytes get() = similar.sumOf { g -> g.sortedByDescending { it.sharpness }.drop(1).sumOf { it.item.size } }
    val blurryBytes get() = blurry.sumOf { it.item.size }
    val screenshotBytes get() = screenshots.sumOf { it.size }
    val reclaimable get() = duplicateBytes + similarBytes + blurryBytes + screenshotBytes
}

data class AppInfo(val pkg: String, val label: String, val appBytes: Long, val dataBytes: Long, val cacheBytes: Long, val lastUsed: Long)

data class SafEntry(val uri: Uri, val name: String, val size: Long, val path: String, val isDir: Boolean, val modified: Long)

data class JunkResult(val apks: List<SafEntry> = emptyList(), val emptyFolders: List<SafEntry> = emptyList(), val treeName: String? = null)
