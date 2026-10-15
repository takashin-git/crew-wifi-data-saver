package app.crewdatasaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.crewdatasaver.R
import app.crewdatasaver.data.AppSettings
import app.crewdatasaver.data.UsageRepository
import app.crewdatasaver.data.UsageSnapshot

@Composable
fun UsageMeter(settings: AppSettings, usage: UsageSnapshot?) {
    if (usage == null) {
        Text(stringResource(R.string.usage_loading))
        return
    }
    val allowance = settings.allowanceMb * UsageRepository.BYTES_PER_MB
    val used = usage.totalBytes
    val fraction = if (allowance > 0) (used.toFloat() / allowance) else 0f
    val remaining = (allowance - used).coerceAtLeast(0)
    val color = when {
        fraction >= 1f -> MaterialTheme.colorScheme.error
        fraction >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.used_of, formatBytes(used), formatBytes(allowance)))
            Text("${(fraction * 100).toInt()}%", color = color)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )
        Text(
            stringResource(R.string.remaining, formatBytes(remaining)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.since, formatDateTime(settings.periodStartMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
