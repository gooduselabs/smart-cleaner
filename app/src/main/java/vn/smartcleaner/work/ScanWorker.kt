package vn.smartcleaner.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import vn.smartcleaner.MainActivity
import vn.smartcleaner.R
import vn.smartcleaner.core.Format
import vn.smartcleaner.data.AppDb
import vn.smartcleaner.data.MediaRepository
import vn.smartcleaner.data.Prefs
import vn.smartcleaner.data.Trash
import java.util.concurrent.TimeUnit

/** Quét nền: chỉ chạy khi đang sạc và pin không yếu, mỗi ngày tối đa 1 lần. */
class ScanWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val db = AppDb.get(applicationContext); val prefs = Prefs(applicationContext)
        Trash(applicationContext, db).expire()
        val r = runCatching { MediaRepository(applicationContext, db).scan(prefs.similarThreshold) }.getOrElse { return Result.retry() }
        val rec = r.reclaimable
        if (rec >= 100L * 1024 * 1024 && rec > prefs.lastReclaimable * 1.2) notify(rec)
        prefs.lastReclaimable = rec
        return Result.success()
    }

    private fun notify(bytes: Long) {
        val c = applicationContext
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Gợi ý dọn dẹp", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CH).setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle("Có thể giải phóng ${Format.bytes(bytes)}")
            .setContentText("Ảnh trùng, ảnh mờ và ảnh chụp màn hình đang chờ bạn xem lại.")
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(c).notify(1, n)
    }

    companion object {
        const val CH = "suggest"; private const val NAME = "daily-scan"
        fun schedule(c: Context, on: Boolean) {
            val wm = WorkManager.getInstance(c)
            if (!on) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<ScanWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build()).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
