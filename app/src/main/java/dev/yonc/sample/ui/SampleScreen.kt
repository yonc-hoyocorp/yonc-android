package dev.yonc.sample.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yonc.sample.SampleViewModel
import dev.yonc.sdk.ModelEntry
import dev.yonc.sdk.attestation.AttestationState
import dev.yonc.sdk.turbo.TurboStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(viewModel: SampleViewModel = viewModel()) {
    Scaffold(topBar = { TopAppBar(title = { Text("YOnC Turbo sample") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
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
                Button(onClick = viewModel::downloadDefault) { Text("Download the default model") }
            }
            models.forEach { entry -> ModelRow(entry, viewModel) }
        }
    }
}

@Composable
private fun ModelRow(entry: ModelEntry, viewModel: SampleViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.info.name, style = MaterialTheme.typography.bodyLarge)
            Text(entry.info.spec.ifEmpty { entry.info.id.value }, style = MaterialTheme.typography.bodySmall)
            if (entry is ModelEntry.Downloading) {
                LinearProgressIndicator(progress = { entry.progress }, modifier = Modifier.fillMaxWidth())
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
                    "This device's battery manager stops background work. Allow it on the vendor screen.",
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
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun yesNo(value: Boolean) = if (value) "yes" else "no"

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
