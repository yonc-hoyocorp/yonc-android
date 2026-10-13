package dev.yonc.sample

import dev.yonc.sdk.turbo.SafeCondition

/**
 * What the user can do about one term of Turbo's safe-state gate, in this app's words. The Turbo
 * card and Turbo's notification both read it, so the two always name the same reasons.
 *
 * Later SDK versions may add terms. The `else` branch keeps this compiling across such an upgrade
 * and shows the new term's name until the app has words for it — so the compiler's "redundant
 * else" warning is suppressed on purpose.
 */
@Suppress("REDUNDANT_ELSE_IN_WHEN")
fun SafeCondition.label(): String = when (this) {
    SafeCondition.Charging -> "Plug in the charger"
    SafeCondition.Wifi -> "Connect to Wi-Fi"
    SafeCondition.ScreenLocked -> "Lock the screen"
    SafeCondition.Thermal -> "Let the phone cool down"
    SafeCondition.BackgroundWork -> "Allow background running"
    else -> name
}
