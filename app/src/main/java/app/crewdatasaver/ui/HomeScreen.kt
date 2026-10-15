package app.crewdatasaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.crewdatasaver.R
import app.crewdatasaver.data.AppSettings
import app.crewdatasaver.data.UsageSnapshot

@Composable
fun HomeScreen(
    modifier: Modifier,
    settings: AppSettings,
    usage: UsageSnapshot?,
    hasUsageAccess: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        val active = settings.blockingEnabled
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (active) R.string.blocking_on else R.string.blocking_off),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        val detail = when {
                            !active -> stringResource(R.string.blocking_off_detail)
                            settings.lockdownActive -> stringResource(R.string.lockdown_active_detail)
                            settings.blockedPackages.isEmpty() -> stringResource(R.string.blocking_none_selected)
                            else -> pluralStringResource(
                                R.plurals.blocking_count, settings.blockedPackages.size,
                                settings.blockedPackages.size,
                            )
                        }
                        Text(detail, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = active, onCheckedChange = { if (it) onStart() else onStop() })
                }
            }
        }

        OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.choose_apps))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.allowance_title), style = MaterialTheme.typography.titleMedium)
                if (!hasUsageAccess) {
                    Text(stringResource(R.string.usage_access_needed_short))
                    Button(onClick = onOpenUsageAccess) { Text(stringResource(R.string.grant_access)) }
                } else {
                    UsageMeter(settings, usage)
                }
            }
        }

        Text(
            stringResource(R.string.home_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
