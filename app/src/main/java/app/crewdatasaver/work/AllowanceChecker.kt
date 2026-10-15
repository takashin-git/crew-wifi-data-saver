package app.crewdatasaver.work

import android.content.Context
import app.crewdatasaver.data.SettingsRepository
import app.crewdatasaver.data.UsageRepository
import app.crewdatasaver.data.UsageSnapshot

object AllowanceChecker {

    val THRESHOLDS = listOf(50, 80, 100)

    /**
     * Reads Wi-Fi usage for the current period, posts a warning when a new threshold is
     * crossed, and records whether the allowance is exhausted (which drives lockdown).
     */
    suspend fun check(context: Context): UsageSnapshot? {
        val repo = SettingsRepository(context)
        val s = repo.current()
        val snap = UsageRepository.queryWifiUsage(context, s.periodStartMillis) ?: return null
        val allowanceBytes = s.allowanceMb * UsageRepository.BYTES_PER_MB
        if (allowanceBytes <= 0) return snap

        val percent = (snap.totalBytes * 100 / allowanceBytes).toInt()
        val crossed = THRESHOLDS.lastOrNull { percent >= it } ?: 0
        val exhausted = percent >= 100

        if (crossed > s.lastThresholdNotified) {
            Notifications.showAllowanceWarning(
                context, crossed, lockdownApplied = exhausted && s.lockdownEnabled && s.blockingEnabled,
            )
        }
        repo.updateAllowanceState(exhausted = exhausted, lastThreshold = maxOf(crossed, s.lastThresholdNotified))
        return snap
    }
}
