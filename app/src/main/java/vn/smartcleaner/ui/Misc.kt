package vn.smartcleaner.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.smartcleaner.core.Format
import vn.smartcleaner.data.TrashEntity
import vn.smartcleaner.data.ThemeMode

@Composable fun TrashScreen(list: List<TrashEntity>, now: Long, onRestore: (List<TrashEntity>) -> Unit, onPurge: (List<TrashEntity>) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Thùng rác", style = MaterialTheme.typography.headlineMedium)
                Text("Tự xoá sau 30 ngày · ${Format.bytes(list.sumOf { it.size })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (list.isNotEmpty()) TextButton({ onRestore(list) }) { Text("Khôi phục hết") }
        }
        if (list.isEmpty()) EmptyState(Icons.Outlined.DeleteOutline, "Thùng rác trống", "Mục bạn dọn sẽ nằm đây 30 ngày, có thể khôi phục bất cứ lúc nào.", Modifier.fillMaxSize())
        else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.key }) { t ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (t.kind == TrashEntity.KIND_MEDIA) Thumb(Uri.parse(t.uri), t.key, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), video = t.mime.startsWith("video"))
                            else Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Android, null) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                val d = Format.daysLeft(t.trashedAt, now)
                                Text("${Format.bytes(t.size)} · còn $d ngày", style = MaterialTheme.typography.bodySmall, color = if (d <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ onRestore(listOf(t)) }) { Icon(Icons.Outlined.RestoreFromTrash, "Khôi phục", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            OutlinedButton({ confirm = true }, Modifier.fillMaxWidth().padding(16.dp).height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xoá vĩnh viễn tất cả") }
        }
    }
    if (confirm) AlertDialog({ confirm = false }, confirmButton = { TextButton({ confirm = false; onPurge(list) }) { Text("Xoá vĩnh viễn") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Huỷ") } }, title = { Text("Xoá vĩnh viễn?") },
        text = { Text("${list.size} mục (${Format.bytes(list.sumOf { it.size })}) sẽ bị xoá và không thể khôi phục.") })
}

@Composable fun SettingsScreen(theme: ThemeMode, bgScan: Boolean, onTheme: (ThemeMode) -> Unit, onBgScan: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Cài đặt", Modifier.statusBarsPadding().padding(vertical = 8.dp), style = MaterialTheme.typography.headlineMedium) }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text("Giao diện", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(ThemeMode.DARK to "Tối", ThemeMode.LIGHT to "Sáng", ThemeMode.SYSTEM to "Hệ thống").forEachIndexed { i, (m, l) ->
                            SegmentedButton(theme == m, { onTheme(m) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quét khi sạc", style = MaterialTheme.typography.titleMedium)
                        Text("Tối đa 1 lần/ngày, chỉ khi đang sạc và pin không yếu. Thông báo khi có ≥ 100 MB có thể dọn.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(bgScan, onBgScan)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cam kết riêng tư", style = MaterialTheme.typography.titleMedium)
                    Promise(Icons.Outlined.WifiOff, "Không có quyền Internet: dữ liệu không thể rời máy")
                    Promise(Icons.Outlined.Block, "Không quảng cáo, không theo dõi, không tài khoản")
                    Promise(Icons.Outlined.Shield, "Không dùng quyền “truy cập mọi tệp”")
                    Promise(Icons.Outlined.Memory, "Không “tăng tốc RAM” giả: Android tự quản lý bộ nhớ")
                }
            }
        }
        item { Text("Dọn Sạch 0.1.0 · Mã nguồn mở MIT", Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun Promise(i: ImageVector, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(i, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text(t, style = MaterialTheme.typography.bodyMedium)
}

private data class Page(val icon: ImageVector, val title: String, val body: String, val c1: Color, val c2: Color)
private val pages = listOf(
    Page(Icons.Outlined.AutoAwesome, "Dọn thông minh, ngay trên máy", "Tìm ảnh trùng, ảnh na ná và ảnh mờ bằng thuật toán chạy hoàn toàn trên điện thoại.", Brand.Teal, Brand.Indigo),
    Page(Icons.Outlined.Swipe, "Vuốt để giữ hay xoá", "Xem lại từng ảnh thật nhanh. So sánh song song kích thước, độ phân giải, EXIF.", Brand.Indigo, Brand.Rose),
    Page(Icons.Outlined.RestoreFromTrash, "Không lo xoá nhầm", "Mọi thứ vào thùng rác 30 ngày, hoàn tác chỉ một chạm.", Brand.Amber, Brand.Teal),
    Page(Icons.Outlined.Shield, "Riêng tư tuyệt đối", "Không Internet, không quảng cáo, không tài khoản. Miễn phí và mã nguồn mở.", Brand.Teal, Brand.Lime),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable fun OnboardingScreen(onDone: () -> Unit, startPage: Int = 0) {
    val ps = rememberPagerState(startPage) { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row { Spacer(Modifier.weight(1f)); TextButton(onDone) { Text("Bỏ qua") } }
        HorizontalPager(ps, Modifier.weight(1f)) { i ->
            val p = pages[i]
            val inf = rememberInfiniteTransition(label = "o")
            val sc by inf.animateFloat(.96f, 1.04f, infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), label = "sc")
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(220.dp).graphicsLayerScale(sc).clip(CircleShape).background(Brush.linearGradient(listOf(p.c1, p.c2))), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(150.dp).clip(CircleShape).background(Color.White.copy(.14f)), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, Modifier.size(80.dp), tint = Color.White)
                    }
                }
                Spacer(Modifier.height(40.dp))
                Text(p.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val w by animateDpAsState(if (ps.currentPage == i) 24.dp else 8.dp, label = "w")
                Box(Modifier.padding(4.dp).height(8.dp).width(w).clip(CircleShape).background(if (ps.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            }
        }
        Button({ if (ps.currentPage == pages.lastIndex) onDone() else scope.launch { ps.animateScrollToPage(ps.currentPage + 1) } },
            Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text(if (ps.currentPage == pages.lastIndex) "Bắt đầu" else "Tiếp tục") }
    }
}
private fun Modifier.graphicsLayerScale(s: Float) = this.then(Modifier.graphicsLayer { scaleX = s; scaleY = s })

@Composable fun PermissionScreen(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        EmptyState(Icons.Outlined.PhotoLibrary, "Cho phép truy cập ảnh & video", "Dọn Sạch cần đọc thư viện để tìm ảnh trùng, mờ và tệp lớn. Mọi phân tích diễn ra trên máy, không gửi đi đâu.") {
            Button(onGrant, Modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Cấp quyền") }
        }
    }
}
