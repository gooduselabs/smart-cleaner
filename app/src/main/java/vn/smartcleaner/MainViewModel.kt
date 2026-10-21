package vn.smartcleaner

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.smartcleaner.core.BestShot
import vn.smartcleaner.core.ShotStats
import vn.smartcleaner.data.*
import vn.smartcleaner.work.ScanWorker

data class UiState(
    val hasMedia: Boolean = false,
    val scanning: Boolean = false, val progress: Float = 0f,
    val storage: StorageSummary? = null,
    val scan: ScanResult = ScanResult(),
    val scannedOnce: Boolean = false,
    val apps: List<AppInfo> = emptyList(), val usageAccess: Boolean = false, val appsLoading: Boolean = false,
    val junk: JunkResult = JunkResult(), val junkLoading: Boolean = false,
    val compress: Map<Long, Float> = emptyMap(),
    val bgScan: Boolean = true,
)

sealed interface Pending {
    data class TrashMedia(val items: List<MediaItem>) : Pending
    data class Restore(val entries: List<TrashEntity>) : Pending
    data class Purge(val entries: List<TrashEntity>) : Pending
}

data class Snack(val text: String, val undo: (() -> Unit)? = null)

@OptIn(UnstableApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as App
    val prefs = a.prefs
    private val _s = MutableStateFlow(UiState(bgScan = prefs.bgScan)); val state = _s.asStateFlow()
    val trash = a.trash.flow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sender = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val snack = MutableSharedFlow<Snack>(extraBufferCapacity = 4)
    private var pending: Pending? = null
    private val compressor by lazy { Compressor(app) }

    init { viewModelScope.launch { a.trash.expire() } }

    fun onPermission(granted: Boolean) {
        _s.update { it.copy(hasMedia = granted) }
        if (granted && !_s.value.scannedOnce && !_s.value.scanning) scan()
    }

    fun scan() = viewModelScope.launch {
        _s.update { it.copy(scanning = true, progress = 0f) }
        val r = runCatching { a.media.scan(prefs.similarThreshold) { p -> _s.update { it.copy(progress = p) } } }.getOrNull()
        val appBytes = withContext(Dispatchers.IO) { if (a.apps.hasUsageAccess()) runCatching { a.apps.list().sumOf { it.appBytes + it.dataBytes } }.getOrDefault(0) else 0 }
        val st = withContext(Dispatchers.IO) { runCatching { a.media.storage(appBytes) }.getOrNull() }
        _s.update { it.copy(scanning = false, scannedOnce = true, scan = r ?: it.scan, storage = st ?: it.storage) }
        r?.let { prefs.lastReclaimable = it.reclaimable }
    }

    fun loadApps() = viewModelScope.launch {
        val ok = a.apps.hasUsageAccess()
        _s.update { it.copy(usageAccess = ok, appsLoading = ok) }
        if (ok) { val l = withContext(Dispatchers.IO) { a.apps.list() }; _s.update { it.copy(apps = l, appsLoading = false) } }
    }
    fun usageIntent() = a.apps.usageAccessIntent()
    fun appIntent(pkg: String) = a.apps.appSettingsIntent(pkg)

    fun setTree(uri: Uri) { prefs.treeUri = uri.toString(); scanJunk() }
    fun scanJunk() = viewModelScope.launch {
        val t = prefs.treeUri ?: return@launch
        _s.update { it.copy(junkLoading = true) }
        val r = runCatching { a.junk.scan(Uri.parse(t)) }.getOrDefault(JunkResult())
        _s.update { it.copy(junk = r, junkLoading = false) }
    }
    fun trashApks(list: List<SafEntry>) = viewModelScope.launch {
        val n = a.trash.trashSaf(list, Uri.parse(prefs.treeUri ?: return@launch))
        snack.tryEmit(Snack("Đã chuyển $n tệp APK vào thùng rác")); scanJunk()
    }
    fun deleteEmptyFolders(list: List<SafEntry>) = viewModelScope.launch {
        val n = list.count { a.trash.emptyFolderDelete(it) }
        snack.tryEmit(Snack("Đã xoá $n thư mục rỗng")); scanJunk()
    }

    // ---- Thùng rác ảnh/video: luôn qua hộp thoại xác nhận của hệ thống ----
    fun trashMedia(items: List<MediaItem>) {
        if (items.isEmpty()) return
        pending = Pending.TrashMedia(items); sender.tryEmit(a.trash.trashRequest(items.map { it.uri }))
    }
    fun restore(list: List<TrashEntity>) = viewModelScope.launch {
        list.filter { it.kind == TrashEntity.KIND_SAF }.forEach { a.trash.restoreSaf(it) }
        val media = list.filter { it.kind == TrashEntity.KIND_MEDIA }
        if (media.isNotEmpty()) { pending = Pending.Restore(media); sender.tryEmit(a.trash.restoreRequest(media.map { Uri.parse(it.uri) })) }
    }
    fun purge(list: List<TrashEntity>) = viewModelScope.launch {
        list.filter { it.kind == TrashEntity.KIND_SAF }.forEach { a.trash.purgeSaf(it) }
        val media = list.filter { it.kind == TrashEntity.KIND_MEDIA }
        if (media.isNotEmpty()) { pending = Pending.Purge(media); sender.tryEmit(a.trash.deleteRequest(media.map { Uri.parse(it.uri) })) }
    }

    fun onSenderResult(ok: Boolean) = viewModelScope.launch {
        val p = pending; pending = null
        if (!ok || p == null) return@launch
        when (p) {
            is Pending.TrashMedia -> {
                a.trash.recordTrashed(p.items)
                val ids = p.items.map { it.id }.toSet(); removeIds(ids)
                snack.tryEmit(Snack("Đã chuyển ${p.items.size} mục vào thùng rác") {
                    viewModelScope.launch { restore(trash.value.filter { t -> p.items.any { it.uri.toString() == t.uri } }) }
                })
            }
            is Pending.Restore -> { a.trash.forget(p.entries); snack.tryEmit(Snack("Đã khôi phục ${p.entries.size} mục")); scan() }
            is Pending.Purge -> { a.trash.forget(p.entries); snack.tryEmit(Snack("Đã xoá vĩnh viễn ${p.entries.size} mục")) }
        }
    }

    private fun removeIds(ids: Set<Long>) = _s.update { s ->
        val r = s.scan
        fun g(l: List<List<PhotoInfo>>) = l.map { gr -> gr.filter { it.item.id !in ids } }.filter { it.size > 1 }
        s.copy(scan = r.copy(duplicates = g(r.duplicates), similar = g(r.similar),
            blurry = r.blurry.filter { it.item.id !in ids }, screenshots = r.screenshots.filter { it.id !in ids },
            oldDownloads = r.oldDownloads.filter { it.id !in ids }, largeFiles = r.largeFiles.filter { it.id !in ids },
            videos = r.videos.filter { it.id !in ids }, photos = r.photos.filter { it.id !in ids }))
    }

    /** Chọn ảnh đẹp nhất trong nhóm: độ nét + ML Kit (mắt mở, cười) + độ phân giải. */
    suspend fun best(group: List<PhotoInfo>): Int {
        val stats = group.map { p ->
            val f = runCatching { a.faces.analyze(p.item.uri) }.getOrNull()
            ShotStats(p.sharpness, p.item.pixels, p.item.size, f?.first, f?.second)
        }
        return BestShot.pick(stats)
    }
    suspend fun exif(uri: Uri) = withContext(Dispatchers.IO) { a.media.exif(uri) }

    fun compress(v: MediaItem, q: Quality, thenTrashOriginal: Boolean) = viewModelScope.launch {
        _s.update { it.copy(compress = it.compress + (v.id to 0f)) }
        val ok = runCatching { compressor.compress(v, q) { p -> _s.update { it.copy(compress = it.compress + (v.id to p)) } } }.isSuccess
        _s.update { it.copy(compress = it.compress - v.id) }
        snack.tryEmit(Snack(if (ok) "Đã nén xong, lưu ở Movies/DonSach" else "Nén thất bại — định dạng có thể không được hỗ trợ"))
        if (ok && thenTrashOriginal) trashMedia(listOf(v))
    }

    fun setTheme(t: ThemeMode) = prefs.setTheme(t)
    fun setBgScan(on: Boolean) { prefs.bgScan = on; _s.update { it.copy(bgScan = on) }; ScanWorker.schedule(getApplication(), on) }
    fun finishOnboarding() { prefs.onboarded = true; ScanWorker.schedule(getApplication(), prefs.bgScan) }
}
