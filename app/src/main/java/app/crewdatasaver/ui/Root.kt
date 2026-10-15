package app.crewdatasaver.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crewdatasaver.MainViewModel
import app.crewdatasaver.R

private enum class Tab(val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Filled.Home),
    Apps(R.string.tab_apps, Icons.Filled.Lock),
    Allowance(R.string.tab_allowance, Icons.Filled.Settings),
}

private enum class Overlay { None, UsageAccessInfo, Allowlist }

@Composable
fun CrewDataSaverRoot(vm: MainViewModel, onRequestStart: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val hasAccess by vm.hasUsageAccess.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }

    val s = settings ?: return

    when (overlay) {
        Overlay.UsageAccessInfo -> {
            UsageAccessScreen(onBack = { overlay = Overlay.None; vm.refreshUsage() })
            return
        }
        Overlay.Allowlist -> {
            AppPickerScreen(
                title = stringResource(R.string.allowlist_title),
                subtitle = stringResource(R.string.allowlist_subtitle),
                apps = apps,
                selected = s.allowlistPackages,
                onToggle = vm::setAllowlisted,
                onBack = { overlay = Overlay.None },
            )
            return
        }
        Overlay.None -> Unit
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            Tab.Home -> HomeScreen(
                modifier = m,
                settings = s,
                usage = usage,
                hasUsageAccess = hasAccess,
                onStart = onRequestStart,
                onStop = vm::stopBlocking,
                onOpenApps = { tab = Tab.Apps },
                onOpenUsageAccess = { overlay = Overlay.UsageAccessInfo },
            )
            Tab.Apps -> AppPickerScreen(
                modifier = m,
                title = stringResource(R.string.apps_title),
                subtitle = stringResource(R.string.apps_subtitle),
                apps = apps,
                selected = s.blockedPackages,
                onToggle = vm::setBlocked,
                onBack = null,
            )
            Tab.Allowance -> AllowanceScreen(
                modifier = m,
                settings = s,
                usage = usage,
                hasUsageAccess = hasAccess,
                onSetAllowance = vm::setAllowanceMb,
                onSetPeriodStart = vm::setPeriodStart,
                onSetLockdown = vm::setLockdownEnabled,
                onEditAllowlist = { overlay = Overlay.Allowlist },
                onOpenUsageAccess = { overlay = Overlay.UsageAccessInfo },
            )
        }
    }
}
