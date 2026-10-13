package dev.yonc.sample

import android.app.Application
import dev.yonc.sdk.attestation.AttestationConfig
import dev.yonc.sdk.di.ClientIdentity
import dev.yonc.sdk.di.Yonc
import dev.yonc.sdk.di.YoncGraph
import dev.yonc.sdk.engine.EngineNotification
import dev.yonc.sdk.turbo.SafeState
import dev.yonc.sdk.turbo.TurboNotification
import dev.yonc.sdk.turbo.TurboNotificationSource
import dev.yonc.sdk.turbo.TurboStats
import dev.yonc.sdk.turbo.TurboStatus

/**
 * A plain [Application] is all the SDK asks for: no base class, no interface, no component
 * factory. [Yonc.create] builds the whole SDK once; the returned [YoncGraph] is the handle and
 * lives as long as the process. Both of the SDK's foreground notifications carry this app's words:
 * the inference engine's ([EngineNotification]) and Turbo's ([turboNotification]).
 */
class SampleApplication : Application() {

    /** This build's attestation config; null = Turbo connects unattested (see [attestationConfig]). */
    private val attestation: AttestationConfig? = attestationConfig()

    /** True when Turbo attests this device — only then is there an account to switch. */
    val attestationConfigured: Boolean get() = attestation != null

    val yonc: YoncGraph by lazy {
        Yonc.create(
            app = this,
            identity = ClientIdentity(
                // How this app introduces itself to the YOnC network.
                name = "yonc-android-sample",
                version = BuildConfig.VERSION_NAME,
                attestation = attestation,
            ),
            // Shown on the inference engine's foreground-service notification while a model is
            // loaded.
            notification = EngineNotification(
                title = getString(R.string.app_name),
                text = "On-device AI engine running",
                iconResId = R.drawable.ic_stat_yonc,
            ),
            // The content of Turbo's foreground notification. The SDK keeps the mechanics — the
            // channel, the ongoing flag, the tap that opens this app — and re-posts it as the
            // state moves.
            turboNotification = TurboNotificationSource(::turboNotification),
        )
    }

    /**
     * Turbo's notification in this app's words. The SDK asks on every change of the status, the
     * session counters, the safe state or [demoted], and re-posts only when the answer differs —
     * about once a second at most. [demoted]: Android's 6-hour foreground limit moved Turbo to the
     * background; it keeps running, slower, until the user opens the app — the SDK's own sentence
     * says so. Every state keeps the same icon and the Live Update request (which also selects the
     * notification channel), so the user sees one steady notification whose words change. On
     * Android 16+ the system decides whether it becomes a status-bar chip showing the short text.
     */
    private fun turboNotification(status: TurboStatus, stats: TurboStats, safeState: SafeState, demoted: Boolean): TurboNotification {
        val words = when {
            demoted -> TurboNotification(title = "Turbo Mode is running slower", text = TurboNotificationSource.DEMOTED_TEXT)
            status == TurboStatus.Working || status == TurboStatus.Connected -> TurboNotification(
                title = "Turbo Mode is on",
                text = "${stats.tasksDone} tasks · ${stats.tokensGenerated} tokens",
                shortText = stats.tasksDone.toString(),
            )
            status == TurboStatus.WaitingForSafeState -> TurboNotification(
                title = "Turbo Mode is waiting",
                // The reasons the Turbo card lists; none are named before the first reading.
                text = "Waiting for: " + safeState.unmet.joinToString { it.label() }.ifEmpty { "a safe state" },
            )
            else -> TurboNotificationSource.Default.notification(status, stats, safeState, demoted)
        }
        return words.copy(smallIconResId = R.drawable.ic_stat_yonc, liveUpdate = true)
    }

    /**
     * Device attestation for Turbo Mode is switched on by supplying the application id YOnC
     * issued for this package + signing certificate and the current user's opaque id — nothing
     * else; the operator-side identifiers live inside the SDK. Without a config the device
     * connects unattested — the network admits that only while enforcement is off. A real host
     * passes the signed-in user's id here on every launch, and `turbo.setReportingLabel` when the
     * user switches accounts while the process lives (see [SampleViewModel.switchAccount]). This
     * sample bakes a fixed id in from local.properties.
     */
    private fun attestationConfig(): AttestationConfig? {
        val applicationId = BuildConfig.YONC_APPLICATION_ID
        val reportingId = BuildConfig.YONC_REPORTING_ID
        if (applicationId.isBlank() || reportingId.isBlank()) return null
        return AttestationConfig(
            applicationId = applicationId,
            externalReportingId = reportingId,
        )
    }
}
