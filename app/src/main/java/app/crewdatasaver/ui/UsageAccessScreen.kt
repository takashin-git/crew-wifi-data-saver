package app.crewdatasaver.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.crewdatasaver.R

@Composable
fun UsageAccessScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.usage_access_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.usage_access_body1))
            Text(stringResource(R.string.usage_access_body2))
            Text(stringResource(R.string.usage_access_body3))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val pkgUri = Uri.parse("package:${context.packageName}")
                    try {
                        // Deep link to this app's entry (honoured on many devices; Android 10+).
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri))
                    } catch (e: ActivityNotFoundException) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (ignored: ActivityNotFoundException) {
                        }
                    }
                },
            ) { Text(stringResource(R.string.open_usage_settings)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.done)) }
        }
    }
}
