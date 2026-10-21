package vn.smartcleaner.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.smartcleaner.core.Format
import java.io.File

/**
 * Thùng rác 30 ngày.
 * - Ảnh/video: MediaStore.createTrashRequest (thùng rác hệ thống, tự xoá sau 30 ngày), có hoàn tác.
 * - Tệp SAF (APK…): sao vào bộ nhớ riêng của app rồi xoá bản gốc; khôi phục bằng cách chép lại.
 * Mọi thao tác ảnh/video đều cần người dùng xác nhận qua hộp thoại hệ thống.
 */
class Trash(private val ctx: Context, private val db: AppDb) {
    private val cr get() = ctx.contentResolver
    private val dir get() = File(ctx.filesDir, "trash").apply { mkdirs() }
    val flow = db.trash().flow()

    fun trashRequest(uris: List<Uri>): IntentSender = MediaStore.createTrashRequest(cr, uris, true).intentSender
    fun restoreRequest(uris: List<Uri>): IntentSender = MediaStore.createTrashRequest(cr, uris, false).intentSender
    fun deleteRequest(uris: List<Uri>): IntentSender = MediaStore.createDeleteRequest(cr, uris).intentSender

    suspend fun recordTrashed(items: List<MediaItem>, now: Long = System.currentTimeMillis()) =
        db.trash().add(items.map { TrashEntity(uri = it.uri.toString(), name = it.name, size = it.size, mime = it.mime, trashedAt = now) })

    suspend fun forget(list: List<TrashEntity>) = db.trash().remove(list)

    suspend fun trashSaf(entries: List<SafEntry>, parent: Uri): Int = withContext(Dispatchers.IO) {
        var ok = 0
        val rec = entries.mapNotNull { e ->
            runCatching {
                val f = File(dir, "${System.nanoTime()}_${e.name}")
                cr.openInputStream(e.uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
                check(DocumentFile.fromSingleUri(ctx, e.uri)!!.delete())
                ok++
                TrashEntity(uri = e.uri.toString(), name = e.name, size = e.size, mime = "application/vnd.android.package-archive",
                    trashedAt = System.currentTimeMillis(), kind = TrashEntity.KIND_SAF, parentUri = parent.toString(), privatePath = f.path)
            }.getOrNull()
        }
        db.trash().add(rec); ok
    }

    suspend fun restoreSaf(t: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val parent = DocumentFile.fromTreeUri(ctx, Uri.parse(t.parentUri))!!
            val dst = parent.createFile(t.mime, t.name)!!
            File(t.privatePath!!).inputStream().use { i -> cr.openOutputStream(dst.uri)!!.use { i.copyTo(it) } }
            File(t.privatePath).delete(); db.trash().remove(listOf(t)); true
        }.getOrDefault(false)
    }

    suspend fun purgeSaf(t: TrashEntity) = withContext(Dispatchers.IO) { t.privatePath?.let { File(it).delete() }; db.trash().remove(listOf(t)) }

    /** Dọn bản ghi đã quá 30 ngày (hệ thống đã tự xoá ảnh; tự xoá bản sao SAF). */
    suspend fun expire(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val old = db.trash().all().filter { Format.daysLeft(it.trashedAt, now) == 0 }
        old.forEach { it.privatePath?.let { p -> File(p).delete() } }
        db.trash().remove(old)
    }

    suspend fun emptyFolderDelete(e: SafEntry) = withContext(Dispatchers.IO) { DocumentFile.fromTreeUri(ctx, e.uri)?.delete() ?: false }
}
