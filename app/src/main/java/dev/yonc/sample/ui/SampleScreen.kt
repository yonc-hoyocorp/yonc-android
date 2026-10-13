package dev.yonc.sample.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yonc.sample.SampleViewModel
import dev.yonc.sample.label
import dev.yonc.sdk.ModelEntry
import dev.yonc.sdk.ModelInfo
import dev.yonc.sdk.attestation.AttestationState
import dev.yonc.sdk.turbo.TurboStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(viewModel: SampleViewModel = viewModel()) {
    Scaffold(topBar = { TopAppBar(title = { Text("YOnC Turbo sample") }) }) { padding ->
        LazyColumn(
            // imePadding keeps the account field above the keyboard (the window is edge-to-edge).
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ModelsCard(viewModel) }
            item { TurboCard(viewModel) }
        }
    }
}

// --- Models -----------------------------------------------------------------------------------

@Composable
private fun ModelsCard(viewModel: SampleViewModel) {
    val models by viewModel.models.collectAsState()
    val defaultId by viewModel.defaultModelId.collectAsState()
    val defaultModel = models.firstOrNull { it.info.id == defaultId }?.info
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Model", style = MaterialTheme.typography.titleMedium)
            Text(
                "Turbo runs the network's tasks on a model stored on this phone. One downloaded model " +
                    "is enough — the network picks which of the downloaded ones to run.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (models.isEmpty()) {
                Text("Fetching the catalogue…", style = MaterialTheme.typography.bodyMedium)
            }
            if (models.none { it is ModelEntry.Local }) {
                Button(onClick = viewModel::downloadDefault) {
                    Text(defaultModel?.let { "Download ${it.name}" } ?: "Download the default model")
                }
            }
            models.forEach { entry -> ModelRow(entry, isDefault = entry.info.id == defaultId, viewModel) }
        }
    }
}

@Composable
private fun ModelRow(entry: ModelEntry, isDefault: Boolean, viewModel: SampleViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.info.name, style = MaterialTheme.typography.bodyLarge)
                if (isDefault) {
                    Text("Default", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(entry.info.summary().ifEmpty { entry.info.id.value }, style = MaterialTheme.typography.bodySmall)
            if (entry is ModelEntry.Downloading) {
                // The total is 0 while unknown: show what arrived, with an indeterminate bar.
                if (entry.totalBytes > 0) {
                    Text(
                        "${formatBytes(entry.receivedBytes)} / ${formatBytes(entry.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(progress = { entry.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(formatBytes(entry.receivedBytes), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        when (entry) {
            is ModelEntry.NotDownloaded -> TextButton(onClick = { viewModel.download(entry.info.id) }) { Text("Download") }
            is ModelEntry.Downloading -> TextButton(onClick = { viewModel.cancelDownload(entry.info.id) }) { Text("Cancel") }
            is ModelEntry.Local -> TextButton(onClick = { viewModel.delete(entry.info.id) }) { Text("Delete") }
        }
    }
}

// --- Turbo Mode -------------------------------------------------------------------------------

@Composable
private fun TurboCard(viewModel: SampleViewModel) {
    val models by viewModel.models.collectAsState()
    val status by viewModel.turboStatus.collectAsState()
    val stats by viewModel.turboStats.collectAsState()
    val safe by viewModel.safeState.collectAsState()
    val attestation by viewModel.attestation.collectAsState()
    val restricted by viewModel.backgroundRestricted.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val activity = LocalContext.current as Activity
    val hasLocalModel = models.any { it is ModelEntry.Local }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Turbo Mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Contributes this phone's idle compute to the network — only while charging, on Wi-Fi, " +
                    "screen locked and cool. You observe the safe state; the SDK evaluates it.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = viewModel::toggleTurbo) {
                Text(if (status == TurboStatus.Off) "Turn Turbo on" else "Turn Turbo off")
            }
            if (!hasLocalModel) {
                Text("No model on the device yet — Turbo waits for the first download.", style = MaterialTheme.typography.bodySmall)
            }

            InfoRow("Status", statusLabel(status))
            if (status == TurboStatus.WaitingForSafeState) {
                // The gate's terms failing right now under the SDK's policy: these, and only these,
                // are the reasons Turbo waits.
                safe.unmet.forEach { Text("• ${it.label()}", style = MaterialTheme.typography.bodyMedium) }
            }
            InfoRow("Tasks done", stats.tasksDone.toString())
            InfoRow("Tokens generated", stats.tokensGenerated.toString())
            stats.lastHalt?.let { InfoRow("Last halt", it) }

            Text("Safe state", style = MaterialTheme.typography.titleSmall)
            InfoRow("Charging", yesNo(safe.isCharging))
            InfoRow("Wi-Fi", yesNo(safe.isWifi))
            InfoRow("Screen locked", yesNo(safe.isScreenLocked))
            InfoRow("Thermal", safe.thermalLevel?.toString() ?: "unknown")
            InfoRow("Safe now", yesNo(safe.isSafe))

            Text("Background work", style = MaterialTheme.typography.titleSmall)
            InfoRow("Restricted by the vendor", yesNo(restricted))
            if (restricted) {
                Text(
                    "This device's battery manager stops background work. Allow background running " +
                        "for this app in its battery settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = viewModel::openBatterySettings) { Text("Open battery settings") }
            }
            InfoRow("Notifications", if (notificationsEnabled) "enabled" else "off")
            if (!notificationsEnabled) {
                OutlinedButton(onClick = { viewModel.requestNotifications(activity) }) { Text("Enable notifications") }
            }

            Text("Attestation", style = MaterialTheme.typography.titleSmall)
            InfoRow("State", attestationLabel(attestation))
            if (attestation is AttestationState.Failed) {
                OutlinedButton(onClick = viewModel::retryAttestation) { Text("Retry attestation") }
            }

            if (viewModel.attestationConfigured) {
                AccountSection(viewModel)
            }
        }
    }
}

/**
 * The account the network credits this phone's work to. A real app calls the switch when its user
 * signs in to another account; this sample takes the new id from a text field.
 */
@Composable
private fun AccountSection(viewModel: SampleViewModel) {
    val error by viewModel.accountError.collectAsState()
    var reportingId by remember { mutableStateOf("") }

    Text("Account", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = reportingId,
        onValueChange = { reportingId = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Reporting id") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
    OutlinedButton(onClick = { viewModel.switchAccount(reportingId) }) { Text("Switch account") }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun yesNo(value: Boolean) = if (value) "yes" else "no"

/** "1B · INT4 · 584 MB": the parts the catalogue fills in, the size left out while unknown (0). */
private fun ModelInfo.summary(): String =
    listOf(tier, quant, sizeBytes.takeIf { it > 0 }?.let(::formatBytes))
        .filterNot { it.isNullOrEmpty() }
        .joinToString(" · ")

/**
 * The SDK reports sizes in bytes; units, rounding and locale are the host's choice. This sample
 * uses decimal units: whole megabytes below a gigabyte ("584 MB"), one decimal above ("1.5 GB").
 */
private fun formatBytes(bytes: Long): String =
    if (bytes >= 1_000_000_000L) {
        String.format(Locale.getDefault(), "%.1f GB", bytes / 1e9)
    } else {
        "${(bytes + 500_000L) / 1_000_000L} MB"
    }

private fun statusLabel(status: TurboStatus): String = when (status) {
    TurboStatus.Off -> "off"
    TurboStatus.WaitingForSafeState -> "on, waiting for a safe state"
    TurboStatus.Connecting -> "connecting"
    TurboStatus.Preparing -> "loading the model the network chose"
    TurboStatus.Connected -> "connected, waiting for tasks"
    TurboStatus.Working -> "working on a task"
    TurboStatus.Error -> "reconnecting"
    TurboStatus.AttestationFailed -> "refused by attestation"
}

private fun attestationLabel(state: AttestationState): String = when (state) {
    AttestationState.NotConfigured -> "not configured (connecting unattested)"
    AttestationState.Idle -> "configured, not attempted yet"
    AttestationState.Enrolling, AttestationState.Admitting -> "in progress"
    is AttestationState.Enrolled -> "enrolled"
    is AttestationState.Unavailable -> "unavailable on this device"
    is AttestationState.Failed -> "refused: ${state.failure}"
}
