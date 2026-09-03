package dev.yonc.sample

import android.app.Application
import dev.yonc.sdk.attestation.AttestationConfig
import dev.yonc.sdk.di.ClientIdentity
import dev.yonc.sdk.di.Yonc
import dev.yonc.sdk.di.YoncGraph
import dev.yonc.sdk.engine.EngineNotification

/**
 * A plain [Application] is all the SDK asks for: no base class, no interface, no component
 * factory. [Yonc.create] builds the whole SDK once; the returned [YoncGraph] is the handle and
 * lives as long as the process.
 */
class SampleApplication : Application() {

    val yonc: YoncGraph by lazy {
        Yonc.create(
            app = this,
            identity = ClientIdentity(
                // How this app introduces itself to the YOnC network.
                name = "yonc-android-sample",
                version = BuildConfig.VERSION_NAME,
                attestation = attestationConfig(),
            ),
            // Shown on the inference engine's foreground-service notification while a model is
            // loaded. The Turbo keep-alive notification is the SDK's own.
            notification = EngineNotification(
                title = getString(R.string.app_name),
                text = "On-device AI engine running",
                iconResId = R.drawable.ic_stat_yonc,
            ),
        )
    }

    /**
     * Device attestation for Turbo Mode is switched on by supplying the application id YOnC
     * issued for this package + signing certificate and the current user's opaque id — nothing
     * else; the operator-side identifiers live inside the SDK. Without a config the device
     * connects unattested — the network admits that only while enforcement is off. The identity
     * is fixed for the process lifetime in this SDK version: a real host reads the signed-in
     * user's id at startup (this sample bakes a fixed one in from local.properties).
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
