package dev.yonc.sample

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yonc.sdk.ModelEntry
import dev.yonc.sdk.ModelId
import dev.yonc.sdk.turbo.TurboStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Everything the screen needs, straight off the SDK: the model store (Turbo needs one model on
 * the device), the Turbo controller, and the Android-only background-work surface. The SDK's
 * StateFlows are exposed as-is — they are already the single source of truth; the only state of
 * the sample's own is a refused account switch ([accountError]).
 */
class SampleViewModel(app: Application) : AndroidViewModel(app) {

    private val yonc = (app as SampleApplication).yonc

    // --- Models -------------------------------------------------------------------------------

    /** Catalogue + on-device state; download progress and completion land here. */
    val models: StateFlow<List<ModelEntry>> = yonc.models.models

    /**
     * The model the network names as this device's default — the one [downloadDefault] fetches.
     * Null until the catalogue has named one.
     */
    val defaultModelId: StateFlow<ModelId?> = yonc.models.defaultModelId

    init {
        // Loads what is already on disk and fetches the catalogue the network offers this device.
        viewModelScope.launch { yonc.models.initialize() }
    }

    /** The one-button path: fetch the model the network designates for this device ([defaultModelId]). */
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

    /** Opens this app's own battery screen where the device has one, else its App info page. */
    fun openBatterySettings() {
        yonc.backgroundWork.openSettings()
    }

    /** From an explicit tap: the permission dialog, or the settings screen once a dialog can't help. */
    fun requestNotifications(activity: Activity) {
        yonc.backgroundWork.requestNotifications(activity, fallbackToSettings = true)
    }

    // --- Account (attestation only) -----------------------------------------------------------

    /** The reporting id rides in the attestation config: without one there is no account to switch. */
    val attestationConfigured: Boolean = getApplication<SampleApplication>().attestationConfigured

    /** Why the last [switchAccount] was refused; null once one is accepted. */
    val accountError: StateFlow<String?>
        field = MutableStateFlow(null)

    /**
     * The user switched accounts: the network credits [label] from now on. Turbo reconnects under
     * it at once if it is on, or uses it at the next start. A label the network would reject
     * (empty, over 128 UTF-8 bytes, surrounding whitespace, control characters) is refused here,
     * before anything changes. The switch lasts while the process lives — a real app passes the
     * signed-in user's id at creation on every launch; this sample starts again with its fixed one.
     */
    fun switchAccount(label: String) {
        accountError.value = try {
            yonc.turbo.setReportingLabel(label)
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "This id is not accepted"
        }
    }
}
