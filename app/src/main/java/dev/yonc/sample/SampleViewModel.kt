package dev.yonc.sample

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yonc.sdk.ModelEntry
import dev.yonc.sdk.ModelId
import dev.yonc.sdk.turbo.TurboStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Everything the screen needs, straight off the SDK: the model store (Turbo needs one model on
 * the device), the Turbo controller, and the Android-only background-work surface. The SDK's
 * StateFlows are exposed as-is — they are already the single source of truth.
 */
class SampleViewModel(app: Application) : AndroidViewModel(app) {

    private val yonc = (app as SampleApplication).yonc

    // --- Models -------------------------------------------------------------------------------

    /** Catalogue + on-device state; download progress and completion land here. */
    val models: StateFlow<List<ModelEntry>> = yonc.models.models

    init {
        // Loads what is already on disk and fetches the catalogue the network offers this device.
        viewModelScope.launch { yonc.models.initialize() }
    }

    /** The one-button path: fetch the model the network designates for this device. */
    fun downloadDefault() {
        viewModelScope.launch { yonc.models.download() }
    }

    fun download(id: ModelId) {
        viewModelScope.launch { yonc.models.download(id) }
    }

    fun cancelDownload(id: ModelId) = yonc.models.cancelDownload(id)

    fun delete(id: ModelId) {
        viewModelScope.launch { yonc.models.delete(id) }
    }

    // --- Turbo Mode ---------------------------------------------------------------------------

    val turboStatus: StateFlow<TurboStatus> = yonc.turbo.status
    val turboStats = yonc.turbo.stats
    val safeState = yonc.turbo.safeState
    val attestation = yonc.turbo.attestation

    /**
     * Nothing to pass: the device offers every downloaded model and the network picks which one to
     * run. Switched on before any download finished, Turbo simply waits for the first model.
     */
    fun toggleTurbo() {
        if (yonc.turbo.status.value == TurboStatus.Off) yonc.turbo.start() else yonc.turbo.stop()
    }

    /** After a sticky attestation refusal (see [attestation]), the user's explicit second try. */
    fun retryAttestation() = yonc.turbo.retryAttestation()

    // --- Background work (Android only) -------------------------------------------------------

    /** True while this device's vendor background manager is known to freeze or kill us. */
    val backgroundRestricted: StateFlow<Boolean> = yonc.backgroundWork.restricted

    /** True while the Turbo notification can actually show (permission + app-level toggle). */
    val notificationsEnabled: StateFlow<Boolean> = yonc.backgroundWork.notificationsEnabled

    /** Opens the deepest vendor battery/background screen reachable on this device. */
    fun openBatterySettings() {
        yonc.backgroundWork.openSettings()
    }

    /** From an explicit tap: the permission dialog, or the settings screen once a dialog can't help. */
    fun requestNotifications(activity: Activity) {
        yonc.backgroundWork.requestNotifications(activity, fallbackToSettings = true)
    }
}
