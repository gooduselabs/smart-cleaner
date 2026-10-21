package vn.smartcleaner

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.smartcleaner.data.*
import vn.smartcleaner.ui.*

/** Ảnh màn hình bằng dữ liệu mẫu (không phải ảnh thật của người dùng). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val dir = System.getProperty("screens.dir") ?: "build/screens"
    private val MB = 1024L * 1024; private val GB = 1024 * MB
    private val now = 1_792_000_000_000L

    private fun img(id: Long, size: Long, w: Int = 4000, h: Int = 3000, t: Long = now - id * 60_000, name: String = "IMG_20260$id.jpg", video: Boolean = false, dur: Long = 0) =
        MediaItem(id, Uri.parse("content://media/external/images/media/$id"), name, size, w, h, t, if (video) "video/mp4" else "image/jpeg", "DCIM/Camera/", video, dur)
    private fun p(id: Long, size: Long, sharp: Double, w: Int = 4000, h: Int = 3000) = PhotoInfo(img(id, size, w, h), id, sharp)

    private val dups = listOf(listOf(p(1, 4 * MB, 420.0), p(2, 4 * MB, 420.0)), listOf(p(3, 3 * MB, 300.0), p(4, 3 * MB, 300.0), p(5, 3 * MB, 300.0)), listOf(p(6, 5 * MB, 380.0), p(7, 5 * MB, 380.0)))
    private val similar = listOf(listOf(p(11, 4 * MB, 510.0), p(12, 3_800_000, 220.0), p(13, 4_100_000, 95.0), p(14, 2 * MB, 330.0, 2000, 1500)), listOf(p(15, 3 * MB, 280.0), p(16, 3 * MB, 410.0)))
    private val videos = listOf(img(31, 1_400 * MB, 3840, 2160, name = "VID_tet_2026.mp4", video = true, dur = 412_000), img(32, 620 * MB, 1920, 1080, name = "VID_sinhnhat.mp4", video = true, dur = 255_000),
        img(33, 210 * MB, 1920, 1080, name = "VID_dalat.mp4", video = true, dur = 96_000))
    private val scan = ScanResult(dups, similar, listOf(p(21, 2 * MB, 20.0), p(22, 3 * MB, 15.0)), (40L..52).map { img(it, 900_000, 1080, 2400, name = "Screenshot_$it.png") },
        listOf(img(60, 30 * MB)), videos.take(2), videos, (1L..30).map { img(100 + it, 3 * MB) })
    private val state = UiState(hasMedia = true, storage = StorageSummary(128 * GB, 23 * GB, 31 * GB, 38 * GB, 4 * GB, 22 * GB), scan = scan, scannedOnce = true)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalFakeThumbs provides true) { AppTheme(mode) { Surface(color = MaterialTheme.colorScheme.background) { content() } } } }
        rule.mainClock.advanceTimeBy(2500)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("$dir/$name.png")
    }

    @Test fun s01_dashboard_dark() = shot("01_dashboard_dark") { DashboardScreen(state, {}, {}) }
    @Test fun s02_dashboard_light() = shot("02_dashboard_light", ThemeMode.LIGHT) { DashboardScreen(state, {}, {}) }
    @Test fun s03_similar_dark() = shot("03_similar_dark") { GroupsScreen("Ảnh tương tự", similar, false, {}, {}, {}) }
    @Test fun s04_compare_dark() = shot("04_compare_dark") { CompareScreen(similar[0], {}, { 0 }, { mapOf("Máy" to "Pixel 9", "ISO" to "100") }, {}) }
    @Test fun s05_swipe_dark() = shot("05_swipe_dark") { SwipeScreen(scan.photos, {}, {}) }
    @Test fun s06_videos_dark() = shot("06_videos_dark") { VideosScreen(videos, mapOf(32L to .42f), {}, { _, _, _ -> }, {}) }
    @Test fun s07_duplicates_light() = shot("07_duplicates_light", ThemeMode.LIGHT) { GroupsScreen("Ảnh trùng lặp", dups, true, {}, {}, {}) }
    @Test fun s08_screenshots_light() = shot("08_screenshots_light", ThemeMode.LIGHT) { MediaGridScreen("Ảnh chụp màn hình", "Cũ nhất ở cuối", scan.screenshots, {}, {}) }
    @Test fun s09_apps_dark() = shot("09_apps_dark") {
        AppsScreen(listOf(AppInfo("a", "TikTok", 410 * MB, 2 * GB, 1_300 * MB, now), AppInfo("b", "Zalo", 300 * MB, 3 * GB, 640 * MB, now), AppInfo("c", "Facebook", 350 * MB, 900 * MB, 410 * MB, now),
            AppInfo("d", "Shopee", 200 * MB, 600 * MB, 220 * MB, now - 40 * 86_400_000L), AppInfo("e", "Grab", 180 * MB, 300 * MB, 96 * MB, 0)), true, false, {}, {}, {})
    }
    @Test fun s10_trash_dark() = shot("10_trash_dark") {
        TrashScreen(listOf(TrashEntity(1, "content://x/1", "IMG_0012.jpg", 4 * MB, "image/jpeg", now - 2 * 86_400_000L), TrashEntity(2, "content://x/2", "VID_cu.mp4", 340 * MB, "video/mp4", now - 28 * 86_400_000L),
            TrashEntity(3, "content://x/3", "zalo_setup.apk", 98 * MB, "application/vnd.android.package-archive", now - 5 * 86_400_000L, TrashEntity.KIND_SAF)), now, {}, {})
    }
    @Test fun s11_settings_light() = shot("11_settings_light", ThemeMode.LIGHT) { SettingsScreen(ThemeMode.LIGHT, true, {}, {}) }
    @Test fun s12_onboarding_dark() = shot("12_onboarding_dark") { OnboardingScreen({}) }
    @Test fun s13_empty_dark() = shot("13_empty_dark") { GroupsScreen("Ảnh trùng lặp", emptyList(), true, {}, {}, {}) }
    @Test fun s14_junk_light() = shot("14_junk_light", ThemeMode.LIGHT) { JunkScreen(JunkResult(), false, false, {}, {}, {}, {}) }
    @Test fun s15_dashboard_scanning() = shot("15_dashboard_scanning") { DashboardScreen(state.copy(scanning = true, progress = .62f), {}, {}) }
}
