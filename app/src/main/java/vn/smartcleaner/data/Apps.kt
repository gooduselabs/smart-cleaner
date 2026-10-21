package vn.smartcleaner.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.net.Uri

/**
 * Dung lượng từng app qua StorageStatsManager (cần quyền "Truy cập dữ liệu sử dụng").
 * Android không cho app khác xoá cache của app khác → chỉ hiển thị và mở trang Cài đặt app.
 */
class Apps(private val ctx: Context) {
    fun hasUsageAccess(): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun appSettingsIntent(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun list(): List<AppInfo> {
        val pm = ctx.packageManager
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        val usage = ctx.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val last = runCatching { usage.queryAndAggregateUsageStats(now - 180L * 86_400_000, now).mapValues { it.value.lastTimeUsed } }.getOrDefault(emptyMap())
        val launcher = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.distinct().filter { it != ctx.packageName }
        return launcher.mapNotNull { p ->
            runCatching {
                val ai = pm.getApplicationInfo(p, 0)
                val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, p, Process.myUserHandle())
                AppInfo(p, pm.getApplicationLabel(ai).toString(), s.appBytes, s.dataBytes, s.cacheBytes, last[p] ?: 0)
            }.getOrNull()
        }.sortedByDescending { it.cacheBytes }
    }
}
