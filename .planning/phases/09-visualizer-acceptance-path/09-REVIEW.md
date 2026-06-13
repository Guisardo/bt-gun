---
phase: 09-visualizer-acceptance-path
reviewed: 2026-06-13T03:48:11Z
depth: deep
files_reviewed: 23
files_reviewed_list:
  - android-host/app/build.gradle.kts
  - android-host/app/src/main/java/com/btgun/host/HostSessionService.kt
  - android-host/app/src/main/java/com/btgun/host/session/DesktopControlClient.kt
  - android-host/app/src/main/java/com/btgun/host/session/VisualizerStatus.kt
  - android-host/app/src/test/java/com/btgun/host/HostSessionServiceLivenessTest.kt
  - android-host/app/src/test/java/com/btgun/host/session/DesktopControlClientTest.kt
  - android-host/app/src/test/java/com/btgun/host/session/VisualizerStatusTest.kt
  - desktop-companion/build.gradle.kts
  - desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/control/ControlServer.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/control/VisualizerStatus.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/DesktopUiEventHub.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/PairingWindow.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerPanels.kt
  - desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerWindow.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/control/ControlChannelTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/DesktopUiEventHubTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/PairingWindowTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerMetricsTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerModelTest.kt
  - desktop-companion/src/test/kotlin/com/btgun/desktop/ui/VisualizerWindowTest.kt
findings:
  critical: 2
  warning: 1
  info: 0
  total: 3
status: issues_found
---

# Phase 09: Code Review Report

**Reviewed:** 2026-06-13T03:48:11Z
**Depth:** deep
**Files Reviewed:** 23
**Status:** issues_found

## Summary

Deep review covered the Android visualizer status publisher, Android control client, desktop control server, desktop UI event hub, visualizer model/window, and their tests. The code compiles and the targeted Gradle test suites pass with the project JDK17/Android SDK workaround, but the production visualizer path still misses required live input and metrics events, and the acceptance checklist can mark recenter proof observed before any recenter happened.

Verification run:

- `GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle test` in `desktop-companion` passed.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/btgun-gradle gradle testDebugUnitTest` in `android-host` passed.

## Critical Issues

### CR-01: Visualizer production wiring drops live input, profile, rejection, and metrics events

**Classification:** BLOCKER
**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/Main.kt:32`
**Issue:** The production `DesktopUiEventListener` only forwards session state, Android visualizer status, and haptic results into `VisualizerWindowCoordinator`. It never wires `onUdpInputReceived`, `onUdpInputRejected`, `onUdpInputStateChanged`, or `onProfileMetadataReceived`, and `VisualizerWindowCoordinator` has no handlers that call `VisualizerModel.withAcceptedInput`, `VisualizerModel.withInputRejection`, `VisualizerModel.withProfileMetadata`, or `VisualizerMetrics.record`. The required model functions exist, and `DesktopUiEventHub` fans those events out, but the actual app never feeds them to the visualizer. Result: VIS-02 and VIS-06 fail in production because live controls, aim axes, packet lifecycle, profile metadata, latency, and packet loss stay at defaults even while UDP input is arriving.
**Fix:**
```kotlin
class VisualizerWindowCoordinator(
    private val windowFactory: VisualizerWindowFactory,
    private val metrics: VisualizerMetrics = VisualizerMetrics(),
) {
    fun onUdpInputReceived(input: UdpReceivedInput, observedElapsedNanos: Long = System.nanoTime()) {
        val metricSnapshot = metrics.record(input, desktopRenderElapsedNanos = observedElapsedNanos)
        model = model
            .withAcceptedInput(input, observedElapsedNanos)
            .withMetrics(metricSnapshot)
        windowFactory.applyModel(model)
    }

    fun onUdpInputRejected(reason: InputReplayRejectReason) {
        model = model.withInputRejection(reason.name.lowercase())
        windowFactory.applyModel(model)
    }

    fun onUdpInputStateChanged(state: InputStreamLifecycleState) {
        model = model.withPacketLifecycle(state)
        windowFactory.applyModel(model)
    }

    fun onProfileMetadataReceived(metadata: ProfileMetadata) {
        model = model.withProfileMetadata(metadata)
        windowFactory.applyModel(model)
    }

    fun onVisualizerStatusReceived(status: VisualizerStatus, observedElapsedNanos: Long = System.nanoTime()) {
        metrics.recordStatus(status, desktopReceivedElapsedNanos = observedElapsedNanos)
        model = modelForVisualizerStatus(model, status, observedElapsedNanos)
        windowFactory.applyModel(model)
    }
}
```
Then wire those callbacks in `Main.kt`:
```kotlin
eventHub.listen(
    DesktopUiEventListener(
        onSessionStateChanged = coordinator::onSessionStateChanged,
        onProfileMetadataReceived = coordinator::onProfileMetadataReceived,
        onVisualizerStatusReceived = coordinator::onVisualizerStatusReceived,
        onUdpInputReceived = coordinator::onUdpInputReceived,
        onUdpInputRejected = coordinator::onUdpInputRejected,
        onUdpInputStateChanged = coordinator::onUdpInputStateChanged,
        onHapticResultReceived = coordinator::onHapticResultReceived,
    ),
)
```

### CR-02: Recenter proof row is observed for idle/non-recenter status

**Classification:** BLOCKER
**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerModel.kt:204`
**Issue:** `withVisualizerStatus` marks `RECENTER_AIM_ZERO` observed for every parsed status packet, regardless of `recenterState` or `lastRecenterElapsedNanos`. Android sends visualizer status immediately after authentication (`HostSessionService.kt:779`) and profile reload (`HostSessionService.kt:595`), and `hostVisualizerStatusFor` can report an aim baseline as ready without a recenter event because motion startup seeds `aimBaseline` (`HostSessionService.kt:1201`). A user can therefore confirm the recenter row from an idle status and get a false Phase 9 checklist pass without actually holding reload and seeing a recenter event.
**Fix:**
```kotlin
fun withVisualizerStatus(status: VisualizerStatus, observedElapsedNanos: Long): VisualizerModel {
    val updated = copy(
        recenter = VisualizerRecenterState(
            aimZeroLabel = "Aim zero: ${status.aimZeroLabel}",
            recenterInstruction = recenter.recenterInstruction,
            lastRecenterLabel = lastRecenterLabel(status),
            lastRecenterElapsedNanos = status.lastRecenterElapsedNanos,
            recenterState = status.recenterState,
            lastStatusObservedElapsedNanos = observedElapsedNanos,
        ),
        rawDebug = rawDebug.copy(enabled = status.rawDebugEnabled, collapsed = !status.rawDebugEnabled),
    )
    val recentered = status.recenterState == "recentered" && status.lastRecenterElapsedNanos != null
    return updated.copy(
        checklistRows = updated.checklistRows.markObservedIf(
            id = VisualizerChecklistRowId.RECENTER_AIM_ZERO,
            condition = recentered,
            source = "Android visualizer recenter status",
            observedElapsedNanos = observedElapsedNanos,
        ),
    ).withProductEvent(
        VisualizerProductEvent(
            type = "recenter_${status.recenterState}",
            sequence = status.statusSequence,
            ageSourceElapsedNanos = observedElapsedNanos,
        ),
    )
}
```
Add a negative test where `recenterState = "idle"` and `lastRecenterElapsedNanos = null` does not observe `RECENTER_AIM_ZERO`.

## Warnings

### WR-01: Clock offset survives control/stream session changes

**Classification:** WARNING
**File:** `desktop-companion/src/main/kotlin/com/btgun/desktop/ui/VisualizerMetrics.kt:69`
**Issue:** `record` resets packet counters when the control or stream session changes, but it keeps the previous `clockOffset`. The next session can reuse a stale GOOD offset before a new Android status packet arrives, producing wrong headline latency and target pass/warn labels for the new session. This is especially risky when reconnecting after Android restart, phone reboot, or a different Android host.
**Fix:** Clear the offset on session-key changes and rely on the new session's visualizer status or UDP estimated offset.
```kotlin
if (sessionKey != nextKey) {
    sessionKey = nextKey
    firstSequence = null
    lastSequence = null
    acceptedCount = 0L
    this.clockOffset = null
}
```
Add a test that records a GOOD status-derived offset for one session, records input for a different session without a new status packet, and asserts the snapshot uses `ESTIMATED` or `UNAVAILABLE` instead of the old GOOD offset.

---

_Reviewed: 2026-06-13T03:48:11Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
