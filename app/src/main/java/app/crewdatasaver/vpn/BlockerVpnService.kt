package app.crewdatasaver.vpn

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.crewdatasaver.MainActivity
import app.crewdatasaver.R
import app.crewdatasaver.data.AppSettings
import app.crewdatasaver.data.SettingsRepository
import app.crewdatasaver.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Blackhole" VPN.
 *
 * Normal mode: the VPN applies ONLY to apps the user chose to block (addAllowedApplication).
 * Every other app bypasses the VPN entirely and uses the network as usual.
 *
 * Lockdown mode (allowance used up + lockdown enabled): the VPN applies to every app EXCEPT
 * this app and the user's allowlist (addDisallowedApplication).
 *
 * Packets arriving on the TUN interface are read and discarded; nothing is ever forwarded.
 */
class BlockerVpnService : VpnService() {

    /** What the tunnel currently captures. */
    private sealed interface Config {
        data object Off : Config
        data class BlockList(val packages: Set<String>) : Config
        data class Lockdown(val allowlist: Set<String>) : Config
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settingsRepo: SettingsRepository
    private var observeJob: Job? = null

    @Volatile private var tun: ParcelFileDescriptor? = null
    private var drainRunning: AtomicBoolean? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { settingsRepo.setBlockingEnabled(false) }
            shutdown()
            return START_NOT_STICKY
        }
        // ACTION_START, or a null intent when the system restarts a sticky service.
        startInForeground(getString(R.string.vpn_notif_starting))
        if (observeJob == null) {
            observeJob = scope.launch {
                settingsRepo.settings
                    .map { it.blockingEnabled to configFor(it) }
                    .distinctUntilChanged()
                    .collect { (enabled, config) ->
                        if (!enabled) {
                            shutdown()
                        } else {
                            applyConfig(config)
                        }
                    }
            }
        }
        return START_STICKY
    }

    private fun configFor(s: AppSettings): Config = when {
        s.lockdownActive -> Config.Lockdown(s.allowlistPackages)
        s.blockedPackages.isEmpty() -> Config.Off
        else -> Config.BlockList(s.blockedPackages)
    }

    @Synchronized
    private fun applyConfig(config: Config) {
        val old = tun
        var (newTun, text) = when (config) {
            Config.Off -> null to getString(R.string.vpn_notif_nothing)
            is Config.BlockList -> {
                val pfd = establishBlockList(config.packages)
                pfd to resources.getQuantityString(
                    R.plurals.vpn_notif_blocking, config.packages.size, config.packages.size,
                )
            }
            is Config.Lockdown -> establishLockdown(config.allowlist) to
                getString(R.string.vpn_notif_lockdown)
        }
        if (newTun == null && config != Config.Off) text = getString(R.string.vpn_notif_error)
        tun = newTun
        stopDrain()
        // Close the previous interface only after the new one is up (avoids a gap that
        // would let blocked apps briefly reach the network).
        closeQuietly(old)
        if (newTun != null) {
            startDrain(newTun)
            dropExistingConnections(config)
        }
        updateNotification(text)
    }

    /**
     * A VPN only captures sockets opened after it is established: connections an app already
     * had open (e.g. TikTok's video streams) keep using Wi-Fi. Restarting the captured apps'
     * background processes forces them to reconnect, and the new connections hit the blackhole.
     * Only background processes are affected; an app in the foreground is left alone.
     */
    private fun dropExistingConnections(config: Config) {
        val targets: Set<String> = when (config) {
            Config.Off -> return
            is Config.BlockList -> config.packages
            is Config.Lockdown -> launchablePackages() - config.allowlist
        }
        val am = getSystemService(ActivityManager::class.java) ?: return
        for (pkg in targets) {
            if (pkg == packageName) continue
            try {
                am.killBackgroundProcesses(pkg)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not restart $pkg", e)
            }
        }
    }

    private fun launchablePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolved.mapTo(HashSet()) { it.activityInfo.packageName }
    }

    private fun baseBuilder(): Builder = Builder()
        .setSession(getString(R.string.app_name))
        .setMtu(MTU)
        // IPv4 + IPv6 default routes so blocked apps cannot leak over either family.
        .addAddress("10.111.222.1", 32)
        .addRoute("0.0.0.0", 0)
        .addAddress("fd00:c0de:da7a::1", 128)
        .addRoute("::", 0)
        // DNS points into the tunnel, so lookups from blocked apps are blackholed too.
        .addDnsServer("10.111.222.2")
        // Non-blocking so the drain thread can notice when it should stop.
        .setBlocking(false)
        .also { b ->
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        }

    private fun establishBlockList(packages: Set<String>): ParcelFileDescriptor? {
        val builder = baseBuilder()
        var added = 0
        for (pkg in packages) {
            if (pkg == packageName) continue
            try {
                builder.addAllowedApplication(pkg)
                added++
            } catch (e: PackageManager.NameNotFoundException) {
                Log.i(TAG, "Skipping uninstalled package $pkg")
            }
        }
        // Never establish with zero allowed apps: that would capture ALL traffic.
        if (added == 0) return null
        return establishSafely(builder)
    }

    private fun establishLockdown(allowlist: Set<String>): ParcelFileDescriptor? {
        val builder = baseBuilder()
        try {
            builder.addDisallowedApplication(packageName)
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
        for (pkg in allowlist) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        return establishSafely(builder)
    }

    private fun establishSafely(builder: Builder): ParcelFileDescriptor? = try {
        builder.establish() // null if the user revoked VPN consent
    } catch (e: Exception) {
        Log.e(TAG, "establish() failed", e)
        null
    }

    /**
     * Reads packets from the TUN and drops them (blackhole). The interface is non-blocking,
     * so the loop sleeps briefly when idle and exits when [stopDrain] is called or the
     * descriptor is closed.
     */
    private fun startDrain(pfd: ParcelFileDescriptor) {
        val running = AtomicBoolean(true)
        drainRunning = running
        val t = Thread({
            val buf = ByteArray(MTU)
            try {
                FileInputStream(pfd.fileDescriptor).use { input ->
                    while (running.get()) {
                        val n = input.read(buf) // 0 when no packet is waiting (EAGAIN)
                        if (n < 0) break
                        if (n == 0) Thread.sleep(IDLE_SLEEP_MS)
                        // Packet discarded.
                    }
                }
            } catch (ignored: IOException) {
                // Descriptor closed: normal shutdown / swap.
            } catch (ignored: InterruptedException) {
            }
        }, "tun-drain")
        t.isDaemon = true
        t.start()
    }

    private fun stopDrain() {
        drainRunning?.set(false)
        drainRunning = null
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, Notifications.ID_VPN, buildNotification(text), type)
        } catch (e: Exception) {
            // e.g. ForegroundServiceTypeNotAllowedException if VPN consent was revoked.
            Log.e(TAG, "startForeground failed", e)
            scope.launch { settingsRepo.setBlockingEnabled(false) }
            stopSelf()
        }
    }

    private fun updateNotification(text: String) {
        Notifications.notifySafely(this, Notifications.ID_VPN, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BlockerVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.vpn_notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    @Synchronized
    private fun shutdown() {
        observeJob?.cancel()
        observeJob = null
        stopDrain()
        closeQuietly(tun)
        tun = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // Another VPN took over or the user revoked consent in system settings.
        scope.launch { settingsRepo.setBlockingEnabled(false) }
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopDrain()
        closeQuietly(tun)
        tun = null
        scope.cancel()
        super.onDestroy()
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        try {
            pfd?.close()
        } catch (ignored: IOException) {
        }
    }

    companion object {
        private const val TAG = "BlockerVpn"
        private const val MTU = 1500
        private const val IDLE_SLEEP_MS = 100L
        const val ACTION_START = "app.crewdatasaver.action.START"
        const val ACTION_STOP = "app.crewdatasaver.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, BlockerVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BlockerVpnService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
