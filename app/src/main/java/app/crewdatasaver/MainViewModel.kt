package app.crewdatasaver

import android.app.Application
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.crewdatasaver.data.AppRepository
import app.crewdatasaver.data.AppSettings
import app.crewdatasaver.data.InstalledApp
import app.crewdatasaver.data.SettingsRepository
import app.crewdatasaver.data.UsageRepository
import app.crewdatasaver.data.UsageSnapshot
import app.crewdatasaver.vpn.BlockerVpnService
import app.crewdatasaver.work.AllowanceChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val settings: StateFlow<AppSettings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    private val _usage = MutableStateFlow<UsageSnapshot?>(null)
    val usage: StateFlow<UsageSnapshot?> = _usage.asStateFlow()

    private val _hasUsageAccess = MutableStateFlow(UsageRepository.hasUsageAccess(app))
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    init {
        loadApps()
        resumeBlockingIfNeeded()
    }

    /**
     * If blocking was left on (e.g. after a reboot or the process was killed), restart the
     * VPN when consent is still held; otherwise reset the toggle so the UI tells the truth.
     */
    private fun resumeBlockingIfNeeded() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (!repo.current().blockingEnabled) return@launch
            if (VpnService.prepare(ctx) == null) {
                BlockerVpnService.start(ctx)
            } else {
                repo.setBlockingEnabled(false)
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { AppRepository.loadLaunchableApps(getApplication()) }
        }
    }

    /** Called on resume and after settings change. */
    fun refreshUsage() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _hasUsageAccess.value = UsageRepository.hasUsageAccess(ctx)
            _usage.value = withContext(Dispatchers.IO) { AllowanceChecker.check(ctx) }
        }
    }

    fun setBlocked(pkg: String, blocked: Boolean) {
        viewModelScope.launch { repo.setBlocked(pkg, blocked) }
    }

    fun setAllowlisted(pkg: String, allowed: Boolean) {
        viewModelScope.launch { repo.setAllowlisted(pkg, allowed) }
    }

    fun setLockdownEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.setLockdownEnabled(enabled)
            refreshUsage()
        }
    }

    fun setAllowanceMb(mb: Long) {
        viewModelScope.launch {
            repo.setAllowanceMb(mb)
            refreshUsage()
        }
    }

    fun setPeriodStart(millis: Long) {
        viewModelScope.launch {
            repo.setPeriodStart(millis)
            refreshUsage()
        }
    }

    /** Call only after VpnService.prepare() consent has been granted. */
    fun startBlocking() {
        viewModelScope.launch {
            repo.setBlockingEnabled(true)
            BlockerVpnService.start(getApplication())
        }
    }

    fun stopBlocking() {
        viewModelScope.launch {
            repo.setBlockingEnabled(false)
            BlockerVpnService.stop(getApplication())
        }
    }
}
