# YOnC SDK — Android sample

A small Jetpack Compose app that integrates the YOnC Android SDK the way a partner app does:
download the model the network assigns to the phone, chat with it **on the device**, and switch
on **Turbo Mode** — the phone contributes its idle compute to the YOnC network while it charges,
and the network reports what each of your users earned.

The app is deliberately plain: one `Application`, one `Activity`, one `ViewModel`, one screen.
Everything the SDK needs from a host is visible in those four files; a fifth holds the words the
screen and the Turbo notification share.

## What's new since 0.3.0

- **0.6.0:** on-device chat — `yonc.localChat` opens a conversation with a downloaded model and
  streams the reply; it runs only while Turbo is off (section 7). `ModelInfo.licenseUrl` links each
  model's licence terms. Reporting a problem: section 9. No breaking change: a 0.5.0 integration
  moves to 0.6.0 without code changes.
- **0.5.0, breaking:** `ModelInfo.sizeLabel` and `ModelInfo.spec` are gone. `ModelInfo.sizeBytes`
  is the exact download size and `ModelEntry.Downloading` carries `receivedBytes` / `totalBytes`;
  the host formats sizes (section 3).
- **0.5.0:** `models.defaultModelId` names this device's default model (section 3);
  `SafeState.unmet` says why Turbo waits, and Turbo's notification takes your words, optionally as
  an Android 16 Live Update (section 4); `backgroundWork.openSettings()` opens the app's own
  battery screen where the device has one (section 5); `turbo.setReportingLabel` switches the
  credited account without a restart (section 6).
- **0.4.1:** the SDK ships its own R8 keep rules — a minified host adds none (section 7).
- **0.4.0:** an optional `RouterEnvironment` for partners running their own Router (section 2).

## Requirements

- Android 8.0+ (`minSdk 26`), **arm64-v8a devices only** — the on-device inference runtime ships
  no other ABI and does not run on the standard emulator. Use a real phone.
- Android Gradle Plugin 9.x with its built-in Kotlin (this sample: AGP 9.2.1 / Kotlin 2.4.0).
  Kotlin 2.3 or newer is required to consume the SDK's metadata.
- Read-only credentials for the YOnC Maven repository, issued by YOnC.

## 1. Add the repository and the dependency

`settings.gradle.kts` — one extra repository next to Google's and Maven Central; transitive
dependencies come from those two, nothing else:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://ymm.jfrog.io/artifactory/android-yonc-gradle-release")
            credentials {
                username = "<your yonc user>"
                password = "<your yonc token>"
            }
            content { includeGroup("dev.yonc") }
        }
    }
}
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.yonc:yonc-android:0.6.1")
}

android {
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging { jniLibs.useLegacyPackaging = true }
}
```

This sample keeps the credentials out of git: copy `local.properties.sample` to
`local.properties` and fill in `yonc.repo.user` / `yonc.repo.token` (or export `YONC_REPO_USER` /
`YONC_REPO_TOKEN`).

Nothing to add to your manifest: the network permission, the foreground-service permissions,
`POST_NOTIFICATIONS` and the SDK's two services merge in from the SDK's own manifest.

## 2. Create the SDK once

```kotlin
class SampleApplication : Application() {
    val yonc: YoncGraph by lazy {
        Yonc.create(
            app = this,
            identity = ClientIdentity(name = "my-app", version = BuildConfig.VERSION_NAME, attestation = null),
            notification = EngineNotification(title = "My app", text = "On-device AI engine running", iconResId = R.drawable.ic_stat),
        )
    }
}
```

`Yonc.create` returns the SDK handle. A plain `Application` is enough: no base class, no
interface, no dependency-injection framework required. The handle exposes `models`, `turbo`,
`localChat` and the Android-only `backgroundWork`.

**Your own Router (optional).** Without an `environment` the SDK talks to the YOnC network its
release was built for. A partner that operates its own Router instance passes where it lives, once
per process:

```kotlin
Yonc.create(
    app = this,
    identity = identity,
    notification = engineNotification,
    environment = RouterEnvironment(
        baseUrl = "https://router.example.com/",
        playIntegrityCloudProjectNumber = 123456789012,   // only if your Router decodes Play Integrity tokens with its own Google Cloud project
    ),
)
```

## 3. One model on the device

Turbo runs the network's tasks on a model stored on the phone, so the host has to get one there
(Turbo switched on earlier simply waits for it). The network serves a catalogue tailored to the handset and names a default:

```kotlin
yonc.models.initialize()                     // scan local files + fetch the catalogue for this device
yonc.models.download()                       // the model the network designates as default for this device
yonc.models.defaultModelId                   // StateFlow<ModelId?>: which model that is
yonc.models.models                           // StateFlow<List<ModelEntry>>: NotDownloaded / Downloading(receivedBytes, totalBytes) / Local
yonc.models.delete(id)
```

"Initialize, then download" is all a host has to do: on a first launch `download()` waits for the
catalogue to arrive. The SDK caches the catalogue, so later launches work offline with whatever is
already downloaded. Model files are 0.5–3.5 GB; recommend Wi-Fi. A downloaded model is a
`ModelEntry.Local` — only the SDK can create one, so holding it is the proof of download.

`defaultModelId` is null before `initialize()` and while no catalogue has named a default; this
sample marks that model's row and names it on the download button. Ids are `ModelId`, a value
class over the catalogue's string: `ModelId("…")` builds one, `.value` reads the text, and two ids
are equal when their text is.

Sizes are bytes, and turning them into text is the host's job (units, rounding, locale).
`ModelInfo.sizeBytes` is the exact download size, 0 when unknown. `ModelEntry.Downloading` carries
`receivedBytes` and `totalBytes`; its `progress` (0..1) derives from them and stays 0 while the
total is unknown (0). This sample shows "1B · INT4 · 584 MB" from `tier`, `quant` and `sizeBytes`,
and "312 MB / 584 MB" with a progress bar while downloading.

`ModelInfo.licenseUrl` is a link to the model's licence terms, served with the catalogue (empty
when the network has none for an entry). Show it beside the model — on the row, or in your About
screen — so your users can read the terms of the model they run.

## 4. Turbo Mode

```kotlin
yonc.turbo.start()       // offer this device to the network — it picks which downloaded model runs
yonc.turbo.stop()

yonc.turbo.status        // StateFlow<TurboStatus>: Off, WaitingForSafeState, Connecting, Preparing, Connected, Working, Error, AttestationFailed
yonc.turbo.stats         // tasks done, tokens generated, last halt reason
yonc.turbo.safeState     // charging / Wi-Fi / screen locked / thermal / vendor restriction + the verdict and its reasons
yonc.turbo.attestation   // NotConfigured, Idle, Enrolling, Enrolled, Admitting, Unavailable, Failed
```

Turbo serves tasks only while the device is in a **safe state**: charging, on Wi-Fi, screen
locked and thermally comfortable. The SDK evaluates it every second; the host only observes and
shows it. While Turbo is on the SDK holds a foreground service with a notification (see section 5 for what
that needs from the user), so the work survives the screen turning off. Tapping that notification opens your launcher activity with the
extra `TurboController.EXTRA_SHOW_TURBO` set — show your Turbo screen when you see it.

### Why Turbo waits

`safeState.unmet` is the set of `SafeCondition`s failing right now under the SDK's policy:
`Charging`, `Wifi`, `ScreenLocked`, `Thermal`, `BackgroundWork`. Show these — and only these — as
the reasons Turbo waits; `isSafe` is exactly `unmet.isEmpty()`. Later SDK versions may add terms,
so a `when` over the enum keeps an `else` branch (this sample's `SafeConditions.kt`).

### Turbo's notification

The SDK owns the notification's mechanics — the channel, the ongoing flag, the tap that opens your
app; the words are yours, from a `TurboNotificationSource` passed to `Yonc.create`:

```kotlin
turboNotification = TurboNotificationSource { status, stats, safeState, demoted ->
    when {
        demoted -> TurboNotification(title = "Turbo Mode is running slower", text = TurboNotificationSource.DEMOTED_TEXT)
        status == TurboStatus.Working -> TurboNotification(title = "Turbo Mode is on", text = "${stats.tasksDone} tasks", shortText = "${stats.tasksDone}")
        else -> TurboNotificationSource.Default.notification(status, stats, safeState, demoted)
    }.copy(smallIconResId = R.drawable.ic_stat, liveUpdate = true)
}
```

- The SDK asks on every change of the status, the session counters, the safe state or `demoted`,
  and re-posts only when the answer differs — about once a second at most. Leave the parameter
  out to keep the SDK's own words (`TurboNotificationSource.Default`).
- `demoted`: Android's 6-hour foreground limit moved Turbo to the background; it keeps running,
  slower, until the user opens the app. `TurboNotificationSource.DEMOTED_TEXT` says exactly that —
  keep those words.
- `liveUpdate = true` asks Android 16+ for a Live Update: a status-bar chip with `shortText` next
  to the small icon. The system decides whether to promote it; otherwise it stays an ordinary
  ongoing notification. The flag also selects the notification channel, so keep it the same in
  every state.

## 5. Battery optimization and notifications

Two things only the user can grant. The SDK detects both and never draws UI of its own; the host
shows the prompt and hands the user to the right system screen.

### Notifications

Turbo runs inside a foreground service, and Android requires a foreground service to show a
notification. On Android 13+ showing it needs the `POST_NOTIFICATIONS` runtime permission. Without
it Turbo still serves — the notification is simply invisible, so the user has no sign that the
phone is working for the network.

```kotlin
yonc.backgroundWork.notificationsEnabled                 // StateFlow<Boolean>: permission granted AND notifications not muted for the app
yonc.backgroundWork.requestNotifications(activity)       // on every launch: the system dialog, only while Android still allows one
yonc.backgroundWork.requestNotifications(activity, fallbackToSettings = true)   // from an explicit tap: opens the app's notification settings once a dialog can no longer help
```

Recommended: call the plain form on launch (this sample does it in `MainActivity.onCreate`), show
a row while `notificationsEnabled` is false and put the `fallbackToSettings = true` form behind
its button. Each dialog spends one of the app's limited asks before Android stops showing the
dialog at all — coordinate with your own notification flow rather than calling both.

### Battery optimization ("background activity")

Several vendors ship battery managers that freeze or kill backgrounded apps even while a
foreground service is running — ZTE/nubia, Xiaomi, Huawei, OPPO, vivo, OnePlus and Samsung
among them. A frozen app keeps its notification but stops serving. The remedy is a per-app
switch on the vendor's own screen ("Background running: Allowed", "No restrictions", or Android's
standard *App info → Battery → Unrestricted*); on the measured ROMs that switch also puts the app
on the system power whitelist, which is what the SDK reads.

```kotlin
yonc.backgroundWork.restricted          // StateFlow<Boolean>: this device is known to restrict us, re-read every second
yonc.backgroundWork.vendorManagerPackage // the detected manager, null on stock Android
yonc.backgroundWork.openSettings()      // this app's own battery screen where the device has one, else its App info page
```

While `restricted` is true the safe state stays false and Turbo waits in `WaitingForSafeState` —
it does not fail and does not retry blindly. Show the explanation and a button when the user
turns Turbo on; the verdict lifts by itself as soon as the user flips the switch, no "I did it"
confirmation is needed. There is no stored claim to expire: if the user flips it back, Turbo
pauses again on the next evaluation.

Seen while testing this sample on a ZTE phone: with Turbo on and the screen locked, the vendor
freezer froze the app despite the foreground service; the moment *Background running → Allowed*
was set, the connection came up within seconds. Devices without a vendor manager report
`restricted = false` from the start.

## 6. Attestation and your user id

The network verifies that a device is genuine (Play Integrity) before it takes part, and reports
per-user totals back to you under an id you choose. Both ride in the identity:

```kotlin
ClientIdentity(
    name = "my-app",
    version = BuildConfig.VERSION_NAME,
    attestation = AttestationConfig(
        applicationId = "<issued by YOnC for your package name + signing certificate>",
        externalReportingId = currentUser.opaqueId,   // your own user id, 1–128 UTF-8 bytes, no surrounding whitespace
    ),
)
```

- `externalReportingId` is opaque to the SDK and the network: never parsed, never logged, no
  personal data required. Pass the signed-in user's id at creation on every launch.
- When the user switches accounts while the process lives, call
  `yonc.turbo.setReportingLabel(newId)`. The network credits the new id from then on: Turbo leaves
  its connection at once (a task in flight is dropped) and reconnects under it; with Turbo off the
  id is used at the next start. It throws `IllegalArgumentException` for an id the network would
  reject and does nothing without an attestation config.
- Everything operator-side (Google Cloud project, attestation environment) ships inside the SDK;
  the two values above are all a host supplies (a partner running its own Router names its Google
  Cloud project in `RouterEnvironment`, section 2).
- Without a config the device connects unattested; the network admits that only while
  enforcement is off for your application.
- On a sticky refusal the SDK parks Turbo in `TurboStatus.AttestationFailed` and stops retrying
  (every attempt costs a Play Integrity quota unit); `turbo.retryAttestation()` is the user's
  explicit second try.
- Devices without Google Play services report `AttestationState.Unavailable` and connect
  unattested.

This sample reads the two values from `local.properties` so it can be tried without an account
system — a real app passes the signed-in user's id. With attestation configured, the Turbo card's
*Account* field calls `setReportingLabel`; the switch lasts until the process ends.

## 7. On-device chat

`yonc.localChat` runs conversations on the phone's own engine with a model already on the device.
Nothing the user types and nothing the model answers leaves the phone: a chat turn sends nothing
to the network.

```kotlin
yonc.localChat.availability                     // StateFlow<ChatAvailability>: Ready, NoModel, TurboOn

val session = yonc.localChat.session(local)     // a new, empty conversation on a ModelEntry.Local
session.send("Salom!").collect { event ->       // Flow<TokenEvent>: cold — collecting runs the turn
    if (!event.thinking) answer.append(event.text)   // reasoning (models that think) is flagged, the answer is plain text
}
session.history                                 // List<ChatMessage>: the committed exchanges, answer text only
session.lastTokenCounts                         // engine-counted input/output tokens of the last completed reply
session.reset()                                 // start over in place
session.close()                                 // end the conversation: history discarded, model unloaded
```

- **Availability.** `Ready`, `NoModel` (nothing downloaded yet) or `TurboOn`. Read it before
  showing the chat entry; this sample disables the message field otherwise. Chat does not gate on
  battery, charging or temperature, needs no attestation and no YOnC consent — only a downloaded
  model.
- **Chat and Turbo share the device's one engine, so chat runs only while Turbo is off.** A message
  sent while Turbo is on fails with `ChatUnavailableException` (its `reason` is `TurboOn`) before
  it reaches the engine; switching Turbo on stops a reply in flight the same way. After Turbo is
  switched off, chat is available again within seconds. Chat never interrupts a network task, so it
  does not touch earnings, Turbo statistics, attestation or the Turbo notification.
- **One conversation at a time.** `session(model)` starts a new, empty conversation. It keeps its
  own history and builds every prompt from it; you pass only the next message. The engine holds one
  conversation: switching between two sessions rebuilds the other's context on its next message.
- **`send` is cold.** Collecting the flow runs the turn; cancelling the collection stops the
  generation. The first message loads the model, which takes seconds on a large model. One reply at
  a time per session. Every `TokenEvent` carries the engine's metrics (`metrics.ttftMs`,
  `metrics.decodeTokenCount`, `metrics.decodeTokS`). A stopped or failed reply keeps its partial
  answer in `history`, and the next message continues the conversation.
- **Errors arrive in the flow:** `ChatUnavailableException` (Turbo on, also mid-reply),
  `ModelChecksumException` (a damaged model file — delete and download again), the engine's own
  errors (a failed load, an engine process that died), `IllegalStateException` on a closed session.
  The next message retries.
- **Lifecycle.** While a chat is in use, the engine keeps the model loaded in its own process,
  with the SDK's engine notification (`EngineNotification`, section 2) showing. `close()` ends the
  conversation for good: a reply in flight stops, the history is discarded and the engine unloads
  the model — the process exits and its notification goes — unless Turbo is on or another
  session's reply is streaming. The session is finished; the next conversation is a new
  `session(model)`, whose first message loads the model again. Call it when your chat screen
  closes (this sample's *Close chat* button and the ViewModel's `onCleared`). If Android ends the
  engine process in the background, the next message loads the model again. Chat adds no
  permission and no foreground-service type to what the SDK already declares for Turbo.
- **Limits.** One reply at a time per session, one conversation at a time on the engine. The
  context window and what happens when a conversation outgrows it are not specified in this
  release: keep conversations short and start over with `reset()` or a new session.
- **Models and languages.** Any downloaded catalogue model can open a conversation (section 3).
  Gemma 3 1B, the model the network assigns by default, is the one chat is checked with; the others
  are possible but not the default. Gemma 3 1B is a small general model: it has not been measured
  in Uzbek or Russian, and a model of this size is not expected to hold a conversation in Uzbek
  well.
- **Generation options.** None in this release: a model's `temperature` from the catalogue sets
  its sampling, and every chat reply runs under a repetition penalty (1.1 over the last 64 tokens,
  the same on Android and iOS) so a small model escapes verbatim loops. A system prompt,
  temperature and output length per conversation are planned; tell YOnC which you need and in
  which chat scenarios.
- **Privacy.** A chat turn sends nothing to the network: the user's messages and the model's
  replies stay on the phone. The SDK uses the network for the model catalogue, model downloads,
  Turbo, and a report when the engine fails to load a model (the model, accelerator, reason and
  memory; never text). The SDK applies no content filtering of its own; the models carry only
  their own training, so any filtering your app needs is on your side.
- **Testing the states.** Delete the downloaded model and availability is `NoModel`; switch Turbo
  on and it is `TurboOn`; with a model on the phone and Turbo off it is `Ready`. Engine failures
  and thermal limits cannot be forced from the app. The smallest model in the catalogue is
  Gemma 3 1B; there is no smaller test model. To test against the YOnC test or UAT Router, pass
  its address in `RouterEnvironment` (section 2) with the application id issued for it.

## 8. Build and run

```
./gradlew :app:installDebug
```

The release build (`./gradlew :app:assembleRelease`) is shrunk with R8 and the default rules
only — the SDK AAR carries its own keep rules, so the host adds none (this sample's
`proguard-rules.pro` is empty on purpose).

## 9. Reporting a problem

The SDK has no debug flag. The inference engine's own lines reach logcat at all times, from the
engine's process (`<your package>:engine`). With a bug report send YOnC:

- the steps to reproduce;
- the SDK version (`dev.yonc:yonc-android:<version>`);
- the device model and Android version;
- a logcat capture around the failure (`adb logcat -d > logcat.txt` right after it happens).

## Known limitations

- arm64-v8a physical devices only; no emulator.
- Downloads run in the foreground and do not resume after an interruption.
- A vendor-restricted device (section 5) cannot serve until the user allows background activity
  on the vendor's own screen; the SDK detects and navigates, the user has to flip the switch.
- Chat and Turbo share the engine: no chat while Turbo is on (section 7). Chat has no system
  prompt, sampling or output-length options yet, and a small model is not expected to hold a
  conversation in every language.

## Project layout

```
app/src/main/java/dev/yonc/sample/
  SampleApplication.kt   Yonc.create + attestation config + Turbo's notification words
  MainActivity.kt        notification permission request + Compose entry
  SampleViewModel.kt     the SDK roots exposed to the UI + the chat conversation + the account switch
  SafeConditions.kt      the words for why Turbo waits (screen and notification)
  ui/SampleScreen.kt     model · on-device chat · Turbo (status, reasons, safe state, background work, attestation, account)
```
