package app.crewdatasaver.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.crewdatasaver.R
import app.crewdatasaver.data.AppSettings
import app.crewdatasaver.data.UsageSnapshot
import java.util.Calendar

@Composable
fun AllowanceScreen(
    modifier: Modifier,
    settings: AppSettings,
    usage: UsageSnapshot?,
    hasUsageAccess: Boolean,
    onSetAllowance: (Long) -> Unit,
    onSetPeriodStart: (Long) -> Unit,
    onSetLockdown: (Boolean) -> Unit,
    onEditAllowlist: () -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var mbText by rememberSaveable { mutableStateOf(settings.allowanceMb.toString()) }
    LaunchedEffect(settings.allowanceMb) { mbText = settings.allowanceMb.toString() }
    val parsed = mbText.trim().toLongOrNull()
    val valid = parsed != null && parsed > 0

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.allowance_title), style = MaterialTheme.typography.headlineSmall)

        if (!hasUsageAccess) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.usage_access_needed_short))
                    Button(onClick = onOpenUsageAccess) { Text(stringResource(R.string.grant_access)) }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { UsageMeter(settings, usage) }
            }
        }

        // Allowance amount
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.allowance_amount), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mbText,
                        onValueChange = { v -> mbText = v.filter { it.isDigit() }.take(7) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.allowance_mb_label)) },
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = valid && parsed != settings.allowanceMb,
                        onClick = { parsed?.let(onSetAllowance); focus.clearFocus() },
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }

        // Period start
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.period_start), style = MaterialTheme.typography.titleMedium)
                Text(formatDateTime(settings.periodStartMillis))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSetPeriodStart(System.currentTimeMillis()) }) {
                        Text(stringResource(R.string.reset_now))
                    }
                    OutlinedButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = settings.periodStartMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val picked = Calendar.getInstance().apply {
                                    set(y, m, d, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                onSetPeriodStart(picked.coerceAtMost(System.currentTimeMillis()))
                            },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }) { Text(stringResource(R.string.pick_date)) }
                }
            }
        }

        // Lockdown
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.lockdown_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.lockdown_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.lockdownEnabled, onCheckedChange = onSetLockdown)
                }
                if (settings.lockdownEnabled) {
                    Text(
                        pluralStringResource(
                            R.plurals.allowlist_count, settings.allowlistPackages.size,
                            settings.allowlistPackages.size,
                        ),
                    )
                    OutlinedButton(onClick = onEditAllowlist) { Text(stringResource(R.string.edit_allowlist)) }
                    if (!settings.blockingEnabled) {
                        Text(
                            stringResource(R.string.lockdown_needs_blocking),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        // Top apps
        if (hasUsageAccess && usage != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.top_apps), style = MaterialTheme.typography.titleMedium)
                    if (usage.topApps.isEmpty()) {
                        Text(stringResource(R.string.top_apps_empty))
                    }
                    usage.topApps.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(a.bytes))
                        }
                    }
                    Text(
                        stringResource(R.string.usage_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
