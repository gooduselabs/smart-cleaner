package vn.smartcleaner.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.smartcleaner.core.Format
import vn.smartcleaner.data.*

private fun dur(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VideosScreen(videos: List<MediaItem>, progress: Map<Long, Float>, onBack: () -> Unit, onCompress: (MediaItem, Quality, Boolean) -> Unit, onTrash: (List<MediaItem>) -> Unit) {
    var sheet by remember { mutableStateOf<MediaItem?>(null) }
    Scaffold(topBar = { AppBar("Video & nén", onBack) }) { pad ->
        if (videos.isEmpty()) EmptyState(Icons.Outlined.VideoLibrary, "Chưa có video", "Video trong máy sẽ hiện ở đây, sắp theo dung lượng.", Modifier.padding(pad).fillMaxSize())
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Video lớn nhất trước. Nén giữ nguyên bản gốc cho tới khi bạn chọn xoá.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(videos, key = { it.id }) { v ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Thumb(v.uri, v.id, Modifier.size(84.dp, 64.dp).clip(RoundedCornerShape(12.dp)), video = true)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${Format.bytes(v.size)} · ${v.height}p · ${dur(v.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Nén 720p ≈ ${Format.bytes(estimateCompressed(v, Quality.MEDIUM))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton({ onTrash(listOf(v)) }) { Icon(Icons.Outlined.Delete, "Xoá") }
                            FilledTonalButton({ sheet = v }, enabled = v.id !in progress, shape = RoundedCornerShape(12.dp)) { Text("Nén") }
                        }
                        AnimatedVisibility(v.id in progress) {
                            Column(Modifier.padding(top = 10.dp)) {
                                LinearProgressIndicator({ progress[v.id] ?: 0f }, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                                Text("Đang nén… ${((progress[v.id] ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
    sheet?.let { v ->
        var q by remember { mutableStateOf(Quality.MEDIUM) }; var del by remember { mutableStateOf(false) }
        ModalBottomSheet({ sheet = null }) {
            Column(Modifier.padding(20.dp, 0.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nén video", style = MaterialTheme.typography.titleLarge)
                Text("${v.name} · ${Format.bytes(v.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Quality.entries.forEach { o ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { q = o }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(q == o, { q = o }); Text(o.label, Modifier.weight(1f))
                        Text("≈ ${Format.bytes(estimateCompressed(v, o))}", fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(del, { del = it }); Text("Chuyển bản gốc vào thùng rác sau khi nén") }
                Text("Ước lượng, dung lượng thực tế tuỳ nội dung video.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ onCompress(v, q, del); sheet = null }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Bắt đầu nén") }
            }
        }
    }
}

@Composable fun JunkScreen(j: JunkResult, loading: Boolean, hasTree: Boolean, onBack: () -> Unit, onPick: () -> Unit, onTrashApks: (List<SafEntry>) -> Unit, onDeleteFolders: (List<SafEntry>) -> Unit) {
    Scaffold(topBar = { AppBar("APK & thư mục rỗng", onBack) { if (hasTree) TextButton(onPick) { Text("Đổi thư mục") } } }) { pad ->
        when {
            !hasTree -> EmptyState(Icons.Outlined.FolderOpen, "Chọn thư mục để quét", "Android chỉ cho phép quét thư mục bạn cấp quyền (không dùng quyền truy cập mọi tệp). Ví dụ: một thư mục trong Download hoặc thẻ nhớ.",
                Modifier.padding(pad).fillMaxSize()) { Button(onPick, shape = RoundedCornerShape(14.dp)) { Text("Chọn thư mục") } }
            loading -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            j.apks.isEmpty() && j.emptyFolders.isEmpty() -> EmptyState(Icons.Outlined.TaskAlt, "Sạch sẽ", "Không có APK hay thư mục rỗng trong “${j.treeName ?: ""}”.", Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (j.apks.isNotEmpty()) {
                    item { Header("Tệp cài đặt APK", "${j.apks.size} tệp · ${Format.bytes(j.apks.sumOf { it.size })}", "Vào thùng rác") { onTrashApks(j.apks) } }
                    items(j.apks, key = { it.uri.toString() }) { Row1(Icons.Outlined.Android, it.name, "${Format.bytes(it.size)} · ${it.path}") }
                }
                if (j.emptyFolders.isNotEmpty()) {
                    item { Header("Thư mục rỗng", "${j.emptyFolders.size} thư mục", "Xoá") { onDeleteFolders(j.emptyFolders) } }
                    items(j.emptyFolders, key = { it.uri.toString() }) { Row1(Icons.Outlined.FolderOff, it.name, it.path) }
                }
            }
        }
    }
}

@Composable private fun Header(t: String, sub: String, action: String, onClick: () -> Unit) = Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) { Text(t, style = MaterialTheme.typography.titleMedium); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    FilledTonalButton(onClick, shape = RoundedCornerShape(12.dp)) { Text(action) }
}
@Composable fun Row1(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, trailing: @Composable () -> Unit = {}) =
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }

@Composable fun AppsScreen(apps: List<AppInfo>, access: Boolean, loading: Boolean, onBack: () -> Unit, onGrant: () -> Unit, onOpen: (String) -> Unit) {
    var sortCache by remember { mutableStateOf(true) }
    val list = if (sortCache) apps.sortedByDescending { it.cacheBytes } else apps.sortedBy { if (it.lastUsed == 0L) Long.MIN_VALUE else it.lastUsed }
    Scaffold(topBar = { AppBar("Ứng dụng & bộ nhớ đệm", onBack) }) { pad ->
        when {
            !access -> EmptyState(Icons.Outlined.QueryStats, "Cần quyền xem dung lượng", "Bật “Truy cập dữ liệu sử dụng” để xem dung lượng & bộ nhớ đệm từng app. Dữ liệu chỉ ở trên máy.",
                Modifier.padding(pad).fillMaxSize()) { Button(onGrant, shape = RoundedCornerShape(14.dp)) { Text("Mở cài đặt") } }
            loading -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("Android không cho ứng dụng khác xoá bộ nhớ đệm của app khác. Chạm vào app → “Bộ nhớ” → “Xoá bộ nhớ đệm”.",
                            Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(sortCache, { sortCache = true }, { Text("Bộ nhớ đệm lớn") })
                        FilterChip(!sortCache, { sortCache = false }, { Text("Ít dùng nhất") })
                    }
                }
                items(list, key = { it.pkg }) { a ->
                    Surface(onClick = { onOpen(a.pkg) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Apps, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text("App ${Format.bytes(a.appBytes)} · Dữ liệu ${Format.bytes(a.dataBytes)}" + if (a.lastUsed > 0) " · dùng ${fmtDate(a.lastUsed).take(10)}" else " · lâu chưa dùng",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Format.bytes(a.cacheBytes), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                Text("đệm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
