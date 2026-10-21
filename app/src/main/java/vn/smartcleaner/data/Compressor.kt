package vn.smartcleaner.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem as M3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File

enum class Quality(val label: String, val height: Int, val bitsPerPixelFactor: Float) {
    HIGH("Cao · 1080p", 1080, 0.10f), MEDIUM("Cân bằng · 720p", 720, 0.08f), SMALL("Nhỏ gọn · 480p", 480, 0.07f)
}

/** Ước lượng dung lượng sau nén (bitrate ≈ w*h*fps*hệ số). Chỉ để tham khảo. */
fun estimateCompressed(src: vn.smartcleaner.data.MediaItem, q: Quality): Long {
    val h = minOf(q.height, src.height.coerceAtLeast(1)); val w = if (src.height > 0) src.width * h / src.height else h * 16 / 9
    val bps = w.toLong() * h * 30 * q.bitsPerPixelFactor + 128_000
    return minOf(src.size, (bps / 8 * (src.durationMs / 1000.0)).toLong())
}

/** Nén video bằng Media3 Transformer (H.264, giảm độ phân giải). Kết quả lưu vào Movies/DonSach. */
@UnstableApi
class Compressor(private val ctx: Context) {
    suspend fun compress(src: vn.smartcleaner.data.MediaItem, q: Quality, onProgress: (Float) -> Unit): Uri = withContext(Dispatchers.Main) {
        val out = File(ctx.cacheDir, "out_${src.id}.mp4").apply { delete() }
        val edited = EditedMediaItem.Builder(M3Item.fromUri(src.uri))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(if (src.height > 0) minOf(q.height, src.height) else q.height))))
            .build()
        val done = CompletableDeferred<Unit>()
        val tr = Transformer.Builder(ctx).setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(c: Composition, r: ExportResult) { done.complete(Unit) }
                override fun onError(c: Composition, r: ExportResult, e: ExportException) { done.completeExceptionally(e) }
            }).build()
        tr.start(edited, out.path)
        val holder = ProgressHolder()
        val poll = launch { while (isActive) { if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f); delay(300) } }
        try { done.await() } catch (e: CancellationException) { tr.cancel(); throw e } finally { poll.cancel() }
        withContext(Dispatchers.IO) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name.substringBeforeLast('.') + "_nen.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DonSach")
                put(MediaStore.MediaColumns.DATE_TAKEN, src.dateTaken)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { o -> out.inputStream().use { it.copyTo(o) } }
            out.delete(); uri
        }
    }
}
