package app.crewdatasaver.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val blockedPackages: Set<String> = emptySet(),
    val allowlistPackages: Set<String> = emptySet(),
    val blockingEnabled: Boolean = false,
    val allowanceMb: Long = DEFAULT_ALLOWANCE_MB,
    val periodStartMillis: Long = 0L,
    val lockdownEnabled: Boolean = false,
    val allowanceExhausted: Boolean = false,
    val lastThresholdNotified: Int = 0,
) {
    /** Lockdown only applies while the blocker is on, lockdown is enabled and allowance is used up. */
    val lockdownActive: Boolean get() = lockdownEnabled && allowanceExhausted

    companion object {
        const val DEFAULT_ALLOWANCE_MB = 1000L
    }
}

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val BLOCKED = stringSetPreferencesKey("blocked_packages")
        val ALLOWLIST = stringSetPreferencesKey("lockdown_allowlist")
        val BLOCKING_ENABLED = booleanPreferencesKey("blocking_enabled")
        val ALLOWANCE_MB = longPreferencesKey("allowance_mb")
        val PERIOD_START = longPreferencesKey("period_start")
        val LOCKDOWN_ENABLED = booleanPreferencesKey("lockdown_enabled")
        val EXHAUSTED = booleanPreferencesKey("allowance_exhausted")
        val LAST_THRESHOLD = intPreferencesKey("last_threshold_notified")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                blockedPackages = p[Keys.BLOCKED] ?: emptySet(),
                allowlistPackages = p[Keys.ALLOWLIST] ?: emptySet(),
                blockingEnabled = p[Keys.BLOCKING_ENABLED] ?: false,
                allowanceMb = p[Keys.ALLOWANCE_MB] ?: AppSettings.DEFAULT_ALLOWANCE_MB,
                periodStartMillis = p[Keys.PERIOD_START] ?: System.currentTimeMillis(),
                lockdownEnabled = p[Keys.LOCKDOWN_ENABLED] ?: false,
                allowanceExhausted = p[Keys.EXHAUSTED] ?: false,
                lastThresholdNotified = p[Keys.LAST_THRESHOLD] ?: 0,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun ensureDefaults() {
        store.edit { p ->
            if (p[Keys.PERIOD_START] == null) p[Keys.PERIOD_START] = System.currentTimeMillis()
        }
    }

    suspend fun setBlocked(pkg: String, blocked: Boolean) {
        store.edit { p ->
            val cur = p[Keys.BLOCKED] ?: emptySet()
            p[Keys.BLOCKED] = if (blocked) cur + pkg else cur - pkg
        }
    }

    suspend fun setAllowlisted(pkg: String, allowed: Boolean) {
        store.edit { p ->
            val cur = p[Keys.ALLOWLIST] ?: emptySet()
            p[Keys.ALLOWLIST] = if (allowed) cur + pkg else cur - pkg
        }
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        store.edit { it[Keys.BLOCKING_ENABLED] = enabled }
    }

    suspend fun setLockdownEnabled(enabled: Boolean) {
        store.edit { it[Keys.LOCKDOWN_ENABLED] = enabled }
    }

    /** Changing the allowance re-arms warnings; the next check recomputes thresholds. */
    suspend fun setAllowanceMb(mb: Long) {
        store.edit {
            it[Keys.ALLOWANCE_MB] = mb
            it[Keys.LAST_THRESHOLD] = 0
            it[Keys.EXHAUSTED] = false
        }
    }

    /** Starting a new period clears warnings and lockdown. */
    suspend fun setPeriodStart(millis: Long) {
        store.edit {
            it[Keys.PERIOD_START] = millis
            it[Keys.LAST_THRESHOLD] = 0
            it[Keys.EXHAUSTED] = false
        }
    }

    suspend fun updateAllowanceState(exhausted: Boolean, lastThreshold: Int) {
        store.edit {
            if (it[Keys.EXHAUSTED] != exhausted) it[Keys.EXHAUSTED] = exhausted
            if (it[Keys.LAST_THRESHOLD] != lastThreshold) it[Keys.LAST_THRESHOLD] = lastThreshold
        }
    }
}
