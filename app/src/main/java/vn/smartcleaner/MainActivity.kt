package vn.smartcleaner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import vn.smartcleaner.core.Format
import vn.smartcleaner.data.MediaItem
import vn.smartcleaner.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val mediaPerms get() = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    private fun granted() = mediaPerms.take(2).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by vm.prefs.theme.collectAsStateWithLifecycle()
            AppTheme(theme) { Surface(color = MaterialTheme.colorScheme.background) { Root() } }
        }
    }

    override fun onResume() { super.onResume(); vm.onPermission(granted()) }

    @Composable private fun Root() {
        var onboarded by remember { mutableStateOf(vm.prefs.onboarded) }
        val s by vm.state.collectAsStateWithLifecycle()
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermission(granted()) }
        val askAll = { permLauncher.launch(mediaPerms + if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()) }
        AnimatedContent(when { !onboarded -> 0; !s.hasMedia -> 1; else -> 2 }, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, label = "root") { st ->
            when (st) {
                0 -> OnboardingScreen({ vm.finishOnboarding(); onboarded = true; askAll() })
                1 -> PermissionScreen(askAll)
                else -> Main(s)
            }
        }
    }

    @Composable private fun Main(s: UiState) {
        val nav = rememberNavController()
        val snackHost = remember { SnackbarHostState() }
        val trash by vm.trash.collectAsStateWithLifecycle()
        val theme by vm.prefs.theme.collectAsStateWithLifecycle()
        val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.onSenderResult(it.resultCode == Activity.RESULT_OK) }
        val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); vm.setTree(uri) }
        }
        LaunchedEffect(Unit) { vm.sender.collect { senderLauncher.launch(IntentSenderRequest.Builder(it).build()) } }
        LaunchedEffect(Unit) { vm.snack.collect { sn ->
            val r = snackHost.showSnackbar(sn.text, actionLabel = if (sn.undo != null) "Hoàn tác" else null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) sn.undo?.invoke()
        } }
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val tabs = listOf(Triple("home", "Tổng quan", Icons.Outlined.Dashboard), Triple("trash", "Thùng rác", Icons.Outlined.Delete), Triple("settings", "Cài đặt", Icons.Outlined.Settings))
        val back: () -> Unit = { nav.popBackStack() }
        val del: (List<MediaItem>) -> Unit = { vm.trashMedia(it) }
        Scaffold(snackbarHost = { SnackbarHost(snackHost) }, contentWindowInsets = WindowInsets(0),
            bottomBar = { if (route in tabs.map { it.first }) NavigationBar {
                tabs.forEach { (r, l, i) -> NavigationBarItem(route == r, { nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                    { BadgedBox({ if (r == "trash" && trash.isNotEmpty()) Badge { Text("${trash.size}") } }) { Icon(i, null) } }, label = { Text(l) }) }
            } }) { pad ->
            NavHost(nav, "home", Modifier.padding(pad), enterTransition = { slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(200)) }, popEnterTransition = { fadeIn(tween(300)) }, popExitTransition = { slideOutHorizontally(tween(250)) { it / 6 } + fadeOut(tween(250)) }) {
                composable("home") { DashboardScreen(s, { vm.scan() }) { t -> nav.navigate(t.name) } }
                composable("trash") { TrashScreen(trash, System.currentTimeMillis(), { vm.restore(it) }, { vm.purge(it) }) }
                composable("settings") { SettingsScreen(theme, s.bgScan, vm::setTheme, vm::setBgScan) }
                composable(Tool.DUPLICATES.name) { GroupsScreen("Ảnh trùng lặp", s.scan.duplicates, true, back, { nav.navigate("cmp/d/$it") }, del) }
                composable(Tool.SIMILAR.name) { GroupsScreen("Ảnh tương tự", s.scan.similar, false, back, { nav.navigate("cmp/s/$it") }, del) }
                composable("cmp/{k}/{i}") { e ->
                    val g = (if (e.arguments?.getString("k") == "d") s.scan.duplicates else s.scan.similar).getOrNull(e.arguments?.getString("i")?.toIntOrNull() ?: -1)
                    if (g == null) LaunchedEffect(Unit) { nav.popBackStack() }
                    else CompareScreen(g, back, vm::best, vm::exif) { del(it); nav.popBackStack() }
                }
                composable(Tool.BLURRY.name) { MediaGridScreen("Ảnh mờ", "Độ nét thấp (đo trên máy)", s.scan.blurry.map { it.item }, back, del, "Không có ảnh mờ") }
                composable(Tool.SCREENSHOTS.name) { MediaGridScreen("Ảnh chụp màn hình", "Cũ nhất ở cuối", s.scan.screenshots, back, del) }
                composable(Tool.DOWNLOADS.name) { MediaGridScreen("Tải xuống cũ", "Ảnh/video trong Download quá 90 ngày", s.scan.oldDownloads, back, del) }
                composable(Tool.LARGE.name) { MediaGridScreen("Tệp lớn", "Ảnh/video ≥ 50 MB", s.scan.largeFiles, back, del) }
                composable(Tool.SWIPE.name) {
                    val items = remember { s.scan.photos.take(200) }
                    SwipeScreen(items, back) { del(it); nav.popBackStack() }
                }
                composable(Tool.VIDEOS.name) { VideosScreen(s.scan.videos, s.compress, back, vm::compress, del) }
                composable(Tool.JUNK.name) {
                    LaunchedEffect(Unit) { if (vm.prefs.treeUri != null && s.junk.treeName == null) vm.scanJunk() }
                    JunkScreen(s.junk, s.junkLoading, vm.prefs.treeUri != null, back, { treeLauncher.launch(null) }, vm::trashApks, vm::deleteEmptyFolders)
                }
                composable(Tool.APPS.name) {
                    LaunchedEffect(Unit) { vm.loadApps() }
                    AppsScreen(s.apps, s.usageAccess, s.appsLoading, back, { startActivity(vm.usageIntent()) }) { startActivity(vm.appIntent(it)) }
                }
            }
        }
    }
}
