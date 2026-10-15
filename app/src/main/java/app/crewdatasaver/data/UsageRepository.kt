package app.crewdatasaver.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.util.Log
import app.crewdatasaver.R

data class AppUsage(val uid: Int, val label: String, val bytes: Long)

data class UsageSnapshot(
    val totalBytes: Long,
    val topApps: List<AppUsage>,
    val queriedAt: Long,
)

object UsageRepository {

    private const val TAG = "UsageRepository"
    const val BYTES_PER_MB = 1_000_000L

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Wi-Fi usage (all apps) from [startMillis] until now. Returns null if not permitted. */
    @Suppress("DEPRECATION") // ConnectivityManager.TYPE_WIFI is still the NetworkStatsManager API
    fun queryWifiUsage(context: Context, startMillis: Long, topN: Int = 10): UsageSnapshot? {
        if (!hasUsageAccess(context)) return null
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = startMillis.coerceAtMost(end - 1)
        return try {
            val perUid = HashMap<Int, Long>()
            var sum = 0L
            val stats = nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, start, end)
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val bytes = bucket.rxBytes + bucket.txBytes
                    perUid[bucket.uid] = (perUid[bucket.uid] ?: 0L) + bytes
                    sum += bytes
                }
            } finally {
                stats.close()
            }
            val device = try {
                nsm.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, start, end)
                    .let { it.rxBytes + it.txBytes }
            } catch (e: Exception) {
                0L
            }
            val pm = context.packageManager
            val top = perUid.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .take(topN)
                .map { (uid, bytes) -> AppUsage(uid, labelForUid(context, pm, uid), bytes) }
            UsageSnapshot(totalBytes = maxOf(sum, device), topApps = top, queriedAt = end)
        } catch (e: Exception) {
            Log.w(TAG, "Usage query failed", e)
            null
        }
    }

    private fun labelForUid(context: Context, pm: PackageManager, uid: Int): String = when (uid) {
        NetworkStats.Bucket.UID_REMOVED -> context.getString(R.string.uid_removed)
        NetworkStats.Bucket.UID_TETHERING -> context.getString(R.string.uid_tethering)
        Process.SYSTEM_UID -> context.getString(R.string.uid_system)
        else -> {
            val pkgs = pm.getPackagesForUid(uid)
            when {
                pkgs.isNullOrEmpty() -> context.getString(R.string.uid_unknown, uid)
                pkgs.size > 1 -> context.getString(R.string.uid_shared, labelFor(pm, pkgs[0]), pkgs.size - 1)
                else -> labelFor(pm, pkgs[0])
            }
        }
    }

    private fun labelFor(pm: PackageManager, pkg: String): String = try {
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
