package app.crewdatasaver.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.crewdatasaver.MainActivity
import app.crewdatasaver.R

object Notifications {
    const val CHANNEL_VPN = "vpn_status"
    const val CHANNEL_ALLOWANCE = "allowance_warnings"
    const val ID_VPN = 1
    const val ID_ALLOWANCE = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_VPN, context.getString(R.string.channel_vpn),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_vpn_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALLOWANCE, context.getString(R.string.channel_allowance),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_allowance_desc) },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    fun notifySafely(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (ignored: SecurityException) {
        }
    }

    fun showAllowanceWarning(context: Context, percent: Int, lockdownApplied: Boolean) {
        val title = if (percent >= 100) {
            context.getString(R.string.warn_title_full)
        } else {
            context.getString(R.string.warn_title_percent, percent)
        }
        val text = when {
            percent >= 100 && lockdownApplied -> context.getString(R.string.warn_text_lockdown)
            percent >= 100 -> context.getString(R.string.warn_text_full)
            else -> context.getString(R.string.warn_text_percent)
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ALLOWANCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notifySafely(context, ID_ALLOWANCE, n)
    }
}
