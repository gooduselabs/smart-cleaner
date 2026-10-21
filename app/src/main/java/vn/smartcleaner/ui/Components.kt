package vn.smartcleaner.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.smartcleaner.core.Format

/** Khi chụp màn hình bằng dữ liệu mẫu: vẽ ảnh giả thay vì đọc MediaStore. */
val LocalFakeThumbs = staticCompositionLocalOf { false }

private val thumbCache = LruCache<String, Bitmap>(120)
private val fakePalette = listOf(
    listOf(Color(0xFFFFB86B), Color(0xFFE2556B)), listOf(Color(0xFF5EEAD4), Color(0xFF2563EB)), listOf(Color(0xFFA7F3D0), Color(0xFF047857)),
    listOf(Color(0xFFFDE68A), Color(0xFFEA580C)), listOf(Color(0xFFC4B5FD), Color(0xFF7C3AED)), listOf(Color(0xFF93C5FD), Color(0xFF0F172A)),
)

@Composable fun Thumb(uri: Uri, seed: Long, modifier: Modifier = Modifier, video: Boolean = false, px: Int = 320) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (LocalFakeThumbs.current) {
            val p = fakePalette[(seed % fakePalette.size).toInt()]
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(p, Offset.Zero, Offset(size.width, size.height)))
                drawCircle(Color.White.copy(.35f), size.minDimension * .16f, Offset(size.width * .72f, size.height * .3f))
                val path = Path().apply { moveTo(0f, size.height); lineTo(size.width * .38f, size.height * .55f); lineTo(size.width * .6f, size.height * .78f)
                    lineTo(size.width * .8f, size.height * .6f); lineTo(size.width, size.height * .85f); lineTo(size.width, size.height); close() }
                drawPath(path, Color.Black.copy(.25f))
            }
        } else {
            val ctx = LocalContext.current
            val key = "$uri@$px"
            val bmp by produceState(thumbCache.get(key), key) {
                if (value == null) value = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) }.getOrNull()?.also { thumbCache.put(key, it) }
                }
            }
            Crossfade(bmp, label = "thumb") { b -> if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        if (video) Icon(Icons.Default.PlayCircle, null, Modifier.align(Alignment.Center).size(32.dp), tint = Color.White.copy(.9f))
    }
}

data class Slice(val label: String, val bytes: Long, val color: Color)

/** Biểu đồ vòng có hiệu ứng vẽ dần. */
@Composable fun RingChart(slices: List<Slice>, total: Long, modifier: Modifier = Modifier, stroke: Dp = 18.dp, center: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) { anim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx(); val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            var start = -90f
            val sum = total.coerceAtLeast(1).toFloat()
            slices.forEach { s ->
                val sweep = 360f * s.bytes / sum * anim.value
                if (sweep > 0.5f) drawArc(s.color, start + 1f, (sweep - 2f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                start += sweep
            }
        }
        center()
    }
}

@Composable fun LegendDot(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable fun ToolCard(icon: ImageVector, tint: Color, title: String, subtitle: String, badge: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(.16f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
                Spacer(Modifier.weight(1f))
                if (badge != null) Text(badge, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
        }
    }
}

@Composable fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val inf = rememberInfiniteTransition(label = "float")
    val dy by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), label = "dy")
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.offset(y = dy.dp).size(112.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(.25f), MaterialTheme.colorScheme.secondary.copy(.25f)))),
            contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
        actions = actions, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}

@Composable fun SelectCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (selected) 1f else 0.85f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    Box(modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(.35f))
        .border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(26.dp), tint = Color.White)
    }
}

/** Thanh hành động dưới cùng: "Xoá N mục · X MB". */
@Composable fun ActionBar(count: Int, bytes: Long, label: String = "Chuyển vào thùng rác", onClick: () -> Unit) {
    AnimatedVisibility(count > 0, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Đã chọn $count", style = MaterialTheme.typography.titleSmall)
                    Text(Format.bytes(bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick, shape = RoundedCornerShape(14.dp)) { Text(label) }
            }
        }
    }
}

@Composable fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color.copy(.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
}
