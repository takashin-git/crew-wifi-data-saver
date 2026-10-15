package app.crewdatasaver

import android.Manifest
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import app.crewdatasaver.ui.CrewDataSaverRoot
import app.crewdatasaver.ui.theme.CrewDataSaverTheme
import app.crewdatasaver.work.Notifications

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) vm.startBlocking()
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Either way, continue. Notifications are best-effort. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            CrewDataSaverTheme {
                CrewDataSaverRoot(vm = vm, onRequestStart = ::requestStart)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshUsage()
    }

    private fun requestStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnConsent.launch(intent) else vm.startBlocking()
    }
}
