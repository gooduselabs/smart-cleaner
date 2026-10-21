package vn.smartcleaner.ui

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.smartcleaner.core.Format
import vn.smartcleaner.data.MediaItem
import vn.smartcleaner.data.PhotoInfo
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi"))
fun fmtDate(t: Long): String = if (t <= 0) "—" else df.format(Date(t))

/** Danh sách nhóm (trùng/tương tự). Mặc định chọn tất cả trừ ảnh nét nhất. */
@Composable fun GroupsScreen(title: String, groups: List<List<PhotoInfo>>, exact: Boolean, onBack: () -> Unit, onOpen: (Int) -> Unit, onTrash: (List<MediaItem>) -> Unit) {
    val selected = remember(groups) { mutableStateMapOf<Long, Boolean>().apply {
        groups.forEach { g -> val best = g.maxBy { it.sharpness }; g.forEach { if (it !== best) put(it.item.id, true) } } } }
    val all = groups.flatten()
    val sel = all.filter { selected[it.item.id] == true }
    Scaffold(topBar = { AppBar(title, onBack) }, bottomBar = { ActionBar(sel.size, sel.sumOf { it.item.size }) { onTrash(sel.map { it.item }) } }) { pad ->
        if (groups.isEmpty()) EmptyState(Icons.Outlined.TaskAlt, "Gọn gàng rồi!", if (exact) "Không có ảnh nào trùng lặp tuyệt đối." else "Không tìm thấy nhóm ảnh tương tự.", Modifier.padding(pad).fillMaxSize())
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(if (exact) "Các ảnh giống hệt nhau từng byte (SHA-256). Giữ lại 1 bản là đủ." else "Ảnh chụp liên tiếp gần giống nhau (dHash). ★ = gợi ý giữ lại.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(groups) { gi, g ->
                val best = g.maxBy { it.sharpness }
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${g.size} ảnh · ${fmtDate(g.first().item.dateTaken)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton({ onOpen(gi) }) { Icon(Icons.Default.CompareArrows, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("So sánh") }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(g, key = { it.item.id }) { p ->
                                val on = selected[p.item.id] == true
                                Box(Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).clickable { selected[p.item.id] = !on }) {
                                    Thumb(p.item.uri, p.item.id, Modifier.fillMaxSize())
                                    if (on) Box(Modifier.fillMaxSize().background(Color.Black.copy(.35f)))
                                    SelectCheck(on, Modifier.align(Alignment.TopEnd).padding(6.dp))
                                    if (p === best) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { Pill("★ Giữ", Color.White) }
                                    Text(Format.bytes(p.item.size), Modifier.align(Alignment.BottomStart).padding(6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** So sánh song song: kích thước, độ phân giải, độ nét, EXIF; gợi ý ảnh đẹp nhất bằng ML Kit. */
@Composable fun CompareScreen(group: List<PhotoInfo>, onBack: () -> Unit, bestOf: suspend (List<PhotoInfo>) -> Int, exifOf: suspend (Uri) -> Map<String, String>, onTrash: (List<MediaItem>) -> Unit) {
    var best by remember(group) { mutableIntStateOf(group.indices.maxByOrNull { group[it].sharpness } ?: 0) }
    var aiDone by remember(group) { mutableStateOf(false) }
    LaunchedEffect(group) { val b = runCatching { bestOf(group) }.getOrDefault(-1); if (b >= 0) best = b; aiDone = true }
    var left by remember(group) { mutableIntStateOf(0) }
    var right by remember(group) { mutableIntStateOf(if (group.size > 1) 1 else 0) }
    val keep = remember(group, best) { mutableStateListOf(best) }
    val trashList = group.indices.filter { it !in keep }.map { group[it].item }
    Scaffold(topBar = { AppBar("So sánh ${group.size} ảnh", onBack) },
        bottomBar = { ActionBar(trashList.size, trashList.sumOf { it.size }, "Giữ ${keep.size}, xoá còn lại") { onTrash(trashList) } }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(left, right).forEach { idx -> ComparePane(group[idx], idx == best, idx in keep, Modifier.weight(1f), exifOf) {
                    if (idx in keep) keep.remove(idx) else keep.add(idx) } }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(10.dp))
                    Text(if (aiDone) "Gợi ý giữ ảnh ${best + 1}: nét nhất, mắt mở & độ phân giải cao (phân tích trên máy)." else "Đang phân tích khuôn mặt và độ nét…",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text("Chạm để chọn ảnh so sánh (trái / phải)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(group) { i, p ->
                    val ring = when (i) { left -> MaterialTheme.colorScheme.primary; right -> MaterialTheme.colorScheme.secondary; else -> Color.Transparent }
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).border(3.dp, ring, RoundedCornerShape(12.dp))
                        .clickable { if (i != left && i != right) { left = right; right = i } }) {
                        Thumb(p.item.uri, p.item.id, Modifier.fillMaxSize())
                        Text("${i + 1}", Modifier.align(Alignment.BottomEnd).padding(4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable private fun ComparePane(p: PhotoInfo, isBest: Boolean, keep: Boolean, modifier: Modifier, exifOf: suspend (Uri) -> Map<String, String>, onToggle: () -> Unit) {
    val exif by produceState(emptyMap<String, String>(), p.item.uri) { value = runCatching { exifOf(p.item.uri) }.getOrDefault(emptyMap()) }
    Surface(onClick = onToggle, modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (keep) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(.8f)) {
                Thumb(p.item.uri, p.item.id, Modifier.fillMaxSize(), px = 720)
                if (isBest) Box(Modifier.padding(8.dp)) { Pill("★ Đẹp nhất", Color.White) }
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { SelectCheck(keep) }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (keep) "Giữ lại" else "Sẽ xoá", style = MaterialTheme.typography.labelLarge, color = if (keep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Meta("Dung lượng", Format.bytes(p.item.size)); Meta("Độ phân giải", "${p.item.width}×${p.item.height}")
                Meta("Độ nét", "${p.sharpness.roundToInt()}"); Meta("Ngày", fmtDate(p.item.dateTaken))
                exif.forEach { (k, v) -> Meta(k, v) }
            }
        }
    }
}
@Composable private fun Meta(k: String, v: String) = Row {
    Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    Text(v, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

/** Lưới chọn nhiều (ảnh mờ, ảnh chụp màn hình, tải xuống cũ, tệp lớn). */
@Composable fun MediaGridScreen(title: String, hint: String, items: List<MediaItem>, onBack: () -> Unit, onTrash: (List<MediaItem>) -> Unit,
                                emptyTitle: String = "Không có gì để dọn", badge: (MediaItem) -> String? = { null }) {
    val selected = remember(items) { mutableStateMapOf<Long, Boolean>() }
    val sel = items.filter { selected[it.id] == true }
    Scaffold(topBar = { AppBar(title, onBack) {
        if (items.isNotEmpty()) TextButton({ val allOn = sel.size == items.size; items.forEach { selected[it.id] = !allOn } }) { Text(if (sel.size == items.size) "Bỏ chọn" else "Chọn hết") }
    } }, bottomBar = { ActionBar(sel.size, sel.sumOf { it.size }) { onTrash(sel) } }) { pad ->
        if (items.isEmpty()) EmptyState(Icons.Outlined.TaskAlt, emptyTitle, "Mục này đang sạch sẽ. Quay lại sau nhé!", Modifier.padding(pad).fillMaxSize())
        else Column(Modifier.padding(pad)) {
            Text("$hint · ${items.size} mục · ${Format.bytes(items.sumOf { it.size })}", Modifier.padding(16.dp, 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(GridCells.Adaptive(108.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.id }) { m ->
                    val on = selected[m.id] == true
                    val sc by animateFloatAsState(if (on) .92f else 1f, label = "sc")
                    Box(Modifier.aspectRatio(1f).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(14.dp)).clickable { selected[m.id] = !on }) {
                        Thumb(m.uri, m.id, Modifier.fillMaxSize(), video = m.isVideo)
                        SelectCheck(on, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        Text(badge(m) ?: Format.bytes(m.size), Modifier.align(Alignment.BottomStart).background(Color.Black.copy(.45f), RoundedCornerShape(topEnd = 8.dp)).padding(5.dp, 2.dp),
                            color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Vuốt phải = giữ, vuốt trái = xoá. Kết thúc phiên mới chuyển vào thùng rác (có hoàn tác). */
@Composable fun SwipeScreen(items: List<MediaItem>, onBack: () -> Unit, onDone: (List<MediaItem>) -> Unit) {
    var index by remember(items) { mutableIntStateOf(0) }
    val toDelete = remember(items) { mutableStateListOf<MediaItem>() }
    val scope = rememberCoroutineScope()
    val offX = remember { Animatable(0f) }
    fun decide(delete: Boolean) {
        if (index >= items.size) return
        if (delete) toDelete += items[index]
        scope.launch { offX.animateTo(if (delete) -1400f else 1400f, tween(220)); index++; offX.snapTo(0f) }
    }
    Scaffold(topBar = { AppBar("Vuốt để dọn", onBack) { if (index > 0) IconButton({ index--; toDelete.remove(items[index]) }) { Icon(Icons.Default.Undo, "Hoàn tác") } } },
        bottomBar = { ActionBar(toDelete.size, toDelete.sumOf { it.size }, "Xong, dọn ${toDelete.size}") { onDone(toDelete.toList()) } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator({ if (items.isEmpty()) 1f else index / items.size.toFloat() }, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(8.dp))
            Text("${minOf(index + 1, items.size)} / ${items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (index >= items.size) EmptyState(Icons.Outlined.Celebration, "Xong lượt này!", "Đã đánh dấu ${toDelete.size} mục để dọn · ${Format.bytes(toDelete.sumOf { it.size })}")
                else {
                    if (index + 1 < items.size) Card(items[index + 1], Modifier.graphicsLayer { scaleX = .94f; scaleY = .94f; translationY = 28f; alpha = .7f })
                    val m = items[index]
                    Box(Modifier.graphicsLayer { translationX = offX.value; rotationZ = offX.value / 40f }
                        .pointerInput(index) {
                            detectDragGestures(onDragEnd = {
                                when { offX.value < -260f -> decide(true); offX.value > 260f -> decide(false); else -> scope.launch { offX.animateTo(0f, spring()) } }
                            }) { ch, d -> ch.consume(); scope.launch { offX.snapTo(offX.value + d.x) } }
                        }) {
                        Card(m, Modifier)
                        val a = (abs(offX.value) / 260f).coerceIn(0f, 1f)
                        if (offX.value < 0) Box(Modifier.align(Alignment.TopEnd).padding(20.dp).graphicsLayer { alpha = a }) { Pill("XOÁ", MaterialTheme.colorScheme.error) }
                        if (offX.value > 0) Box(Modifier.align(Alignment.TopStart).padding(20.dp).graphicsLayer { alpha = a }) { Pill("GIỮ", MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                LargeFloatingActionButton({ decide(true) }, containerColor = MaterialTheme.colorScheme.error.copy(.16f), contentColor = MaterialTheme.colorScheme.error) { Icon(Icons.Default.Close, "Xoá", Modifier.size(34.dp)) }
                LargeFloatingActionButton({ decide(false) }, containerColor = MaterialTheme.colorScheme.primary.copy(.16f), contentColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Favorite, "Giữ", Modifier.size(34.dp)) }
            }
        }
    }
}

@Composable private fun Card(m: MediaItem, modifier: Modifier) {
    Surface(modifier.fillMaxWidth().aspectRatio(.75f), shape = RoundedCornerShape(28.dp), shadowElevation = 8.dp) {
        Box {
            Thumb(m.uri, m.id, Modifier.fillMaxSize(), video = m.isVideo, px = 900)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.7f)))).padding(18.dp)) {
                Text(m.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${Format.bytes(m.size)} · ${m.width}×${m.height} · ${fmtDate(m.dateTaken)}", color = Color.White.copy(.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
