package vn.smartcleaner.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.smartcleaner.UiState
import vn.smartcleaner.core.Format

enum class Tool { DUPLICATES, SIMILAR, BLURRY, SWIPE, SCREENSHOTS, LARGE, VIDEOS, DOWNLOADS, JUNK, APPS }

@Composable fun DashboardScreen(s: UiState, onScan: () -> Unit, onTool: (Tool) -> Unit) {
    val st = s.storage; val r = s.scan
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dọn Sạch", style = MaterialTheme.typography.headlineMedium)
                    Text("Riêng tư · Không quảng cáo · Chạy trên máy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onScan, enabled = !s.scanning) { Icon(Icons.Default.Refresh, "Quét lại") }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val slices = if (st == null) emptyList() else listOf(
                        Slice("Ảnh", st.images, Brand.Teal), Slice("Video", st.videos, Brand.Indigo), Slice("Âm thanh", st.audio, Brand.Amber),
                        Slice("Ứng dụng", st.apps, Brand.Sky), Slice("Khác", st.other, Brand.Slate))
                    RingChart(slices, st?.total ?: 1, Modifier.size(150.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val pct = if (st == null || st.total == 0L) 0 else (st.used * 100 / st.total).toInt()
                            Text("$pct%", style = MaterialTheme.typography.headlineMedium)
                            Text("đã dùng", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (st != null) {
                            Text("${Format.bytes(st.free)} trống", style = MaterialTheme.typography.titleMedium)
                            Text("trên ${Format.bytes(st.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            slices.forEach { LegendDot(it.color, it.label, Format.bytes(it.bytes)) }
                        } else Text("Đang đọc bộ nhớ…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { ReclaimCard(s, onScan) { onTool(Tool.SWIPE) } }
        item { Text("Ảnh", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        item {
            Grid(listOf(
                Triple(Tool.DUPLICATES, "Ảnh trùng lặp", "${r.duplicates.size} nhóm · ${Format.bytes(r.duplicateBytes)}"),
                Triple(Tool.SIMILAR, "Ảnh tương tự", "${r.similar.size} nhóm · chọn ảnh đẹp nhất"),
                Triple(Tool.BLURRY, "Ảnh mờ", "${r.blurry.size} ảnh · ${Format.bytes(r.blurryBytes)}"),
                Triple(Tool.SCREENSHOTS, "Ảnh chụp màn hình", "${r.screenshots.size} ảnh · ${Format.bytes(r.screenshotBytes)}"),
            ), s.scannedOnce, onTool)
        }
        item { Text("Tệp & ứng dụng", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        item {
            Grid(listOf(
                Triple(Tool.VIDEOS, "Video & nén", "${r.videos.size} video · ${Format.bytes(r.videos.sumOf { it.size })}"),
                Triple(Tool.LARGE, "Tệp lớn", "${r.largeFiles.size} tệp ≥ 50 MB"),
                Triple(Tool.DOWNLOADS, "Tải xuống cũ", "${r.oldDownloads.size} mục > 90 ngày"),
                Triple(Tool.JUNK, "APK & thư mục rỗng", "Quét thư mục bạn chọn"),
                Triple(Tool.APPS, "Ứng dụng & bộ nhớ đệm", "Xem dung lượng, mở cài đặt"),
            ), s.scannedOnce, onTool)
        }
    }
}

@Composable private fun ReclaimCard(s: UiState, onScan: () -> Unit, onSwipe: () -> Unit) {
    val grad = Brush.linearGradient(listOf(Brand.Teal, Brand.Indigo))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(grad).padding(20.dp)) {
        AnimatedContent(s.scanning, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "scan") { scanning ->
            if (scanning) Column {
                Text("Đang phân tích thư viện…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Băm ảnh và đo độ nét ngay trên máy, không tải lên đâu cả.", color = Color.White.copy(.85f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                val p by animateFloatAsState(s.progress, label = "p")
                LinearProgressIndicator({ p }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Color.White, trackColor = Color.White.copy(.25f))
            } else Column {
                Text("Có thể giải phóng", color = Color.White.copy(.85f), style = MaterialTheme.typography.labelLarge)
                Text(Format.bytes(s.scan.reclaimable), color = Color.White, style = MaterialTheme.typography.displaySmall)
                Text("Trùng lặp, tương tự, mờ và ảnh chụp màn hình", color = Color.White.copy(.85f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onSwipe, colors = ButtonDefaults.buttonColors(Color.White, Color(0xFF0F172A)), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Swipe, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Vuốt để dọn")
                    }
                    OutlinedButton(onScan, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.6f))) { Text("Quét lại") }
                }
            }
        }
    }
}

private fun meta(t: Tool) = when (t) {
    Tool.DUPLICATES -> Icons.Outlined.FileCopy to Brand.Teal
    Tool.SIMILAR -> Icons.Outlined.AutoAwesome to Brand.Indigo
    Tool.BLURRY -> Icons.Outlined.BlurOn to Brand.Amber
    Tool.SCREENSHOTS -> Icons.Outlined.Screenshot to Brand.Sky
    Tool.VIDEOS -> Icons.Outlined.VideoLibrary to Brand.Rose
    Tool.LARGE -> Icons.Outlined.SdStorage to Brand.Indigo
    Tool.DOWNLOADS -> Icons.Outlined.Download to Brand.Lime
    Tool.JUNK -> Icons.Outlined.FolderOff to Brand.Amber
    Tool.APPS -> Icons.Outlined.Apps to Brand.Sky
    Tool.SWIPE -> Icons.Outlined.Swipe to Brand.Teal
}

@Composable private fun Grid(items: List<Triple<Tool, String, String>>, ready: Boolean, onTool: (Tool) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (t, title, sub) ->
                    val (ic, c) = meta(t)
                    ToolCard(ic, c, title, if (ready || t == Tool.JUNK || t == Tool.APPS) sub else "Chưa quét", null, Modifier.weight(1f)) { onTool(t) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
