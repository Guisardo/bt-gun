# Phase 03: LAN Pairing and Secure Session - Research

**Researched:** 2026-06-07  
**Domain:** Android-to-desktop LAN pairing, authenticated local session, reliable control channel  
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Pairing Entry Flow
- **D-01:** The normal path is desktop-initiated pairing. Desktop starts a local pairing session and shows a QR code; Android scans that QR to pair.
- **D-02:** QR fallback should be visible manual entry of endpoint details plus pairing code. This is a fallback/debug path, not the primary product path.
- **D-03:** After successful pairing, Android should remember a trusted desktop and let the user choose it later, with reauthentication when required. Do not force QR pairing every session, and do not silently auto-reconnect as the primary behavior.
- **D-04:** QR content should include endpoint information plus one-time authentication material: at minimum protocol version, session id or equivalent, endpoint host/port, expiry, and secret/key-agreement material. Exact schema is planner discretion.

### Discovery and Addressing
- **D-05:** Android should find the desktop from QR-provided endpoint data in the normal path. LAN service discovery is not required for v1 normal pairing.
- **D-06:** Desktop should advertise its best active LAN IPv4 address and port in the QR code and manual fallback UI.
- **D-07:** The Android pairing screen should expose an "Enter manually" style fallback next to QR scan. Full manual mode is not first-class, but the fallback must be discoverable.
- **D-08:** If the QR endpoint is stale or unreachable, Android should show a clear error and allow rescan or manual edit. Do not silently broaden into LAN discovery.

### Session Security
- **D-09:** Pairing must establish an authenticated encrypted local session before any trusted control messages are accepted.
- **D-10:** One-time pairing material should be short-lived, roughly 2-5 minutes. Long-lived pairing windows are not acceptable.
- **D-11:** Manual fallback uses a 6-digit pairing code with short TTL, rate limiting, and binding to the desktop pairing session.
- **D-12:** Android should remember desktop public key or fingerprint as the durable trust anchor. Desktop name and IP are display/addressing metadata, not security identity.
- **D-13:** Reconnect must detect changed or impersonated desktop identity and surface it as an explicit trust problem.

### Reliable Control Channel
- **D-14:** Phase 3 should establish a WebSocket-style reliable bidirectional control channel inside the authenticated encrypted session. Raw TCP framing is allowed only if planning proves it materially simpler without losing debuggability.
- **D-15:** Phase 3 control messages cover pairing state, session lifecycle, heartbeat/liveness, pairing/control diagnostics, and minimal profile metadata required by `TRAN-06`.
- **D-16:** Haptic command support in Phase 3 is envelope reservation only. Define a generic control message envelope and reserve the haptic command type for Phase 4. Do not define the full haptic payload schema, do not vibrate the Android phone, and do not return execution ack/fail semantics in Phase 3.
- **D-17:** Heartbeat/liveness should be bidirectional with timeout states. Android and desktop UI should distinguish connected, degraded, and disconnected session state.
- **D-18:** Phase 3 diagnostics should show pairing/control status only: session state, desktop identity, heartbeat age, and last control error. Packet loss, jitter, high-rate frame metrics, and visualizer latency belong to later phases.

### the agent's Discretion
- Choose exact QR payload format, URI scheme, serialization, key agreement, authenticated encryption mechanism, WebSocket library, control message names, and timeout intervals during planning.
- Choose exact desktop scaffold shape and UI toolkit for the first companion pairing surface, as long as it stays portable to Windows 11 x64 and macOS Apple Silicon.
- Choose minimal profile metadata needed to satisfy `TRAN-06`; full profile storage, mapping, and editing remain Phase 8.
- Choose exact Android storage mechanism for trusted desktop identity, provided the durable trust anchor is a desktop public key or fingerprint.

### Deferred Ideas (OUT OF SCOPE)
- High-rate UDP input frames, packet schemas, replay/stale-input rejection, and desktop input parsing remain Phase 4.
- Phone haptic command payloads, execution, TTL handling, and ack/fail semantics remain Phase 4.
- Full profile storage, profile editing, and aim/button mapping remain Phase 8.
- Packet-loss, jitter, frame-rate, and visualizer latency metrics remain Phase 9 or Phase 10 as mapped in requirements.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TRAN-01 | Desktop companion can create a local pairing session and display a QR code plus pairing-code fallback. [VERIFIED: .planning/REQUIREMENTS.md] | Use a JVM desktop companion with Ktor HTTPS/WebSocket listener, Swing QR/manual fallback surface, ZXing QR generation, and one-time pairing session state. [CITED: https://ktor.io/docs/server-websockets.html] [CITED: https://zxing.github.io/zxing/dependency-info.html] |
| TRAN-02 | Android host app can pair to the desktop companion using QR code or pairing code without manual IP entry in the normal path. [VERIFIED: .planning/REQUIREMENTS.md] | Use Google Play services Code Scanner for QR normal path and existing Android native Activity style for visible manual fallback. [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner] [VERIFIED: local code grep] |
| TRAN-03 | Pairing creates an authenticated local session with a short-lived one-time secret and replay protection. [VERIFIED: .planning/REQUIREMENTS.md] | Use WSS/TLS with pinned desktop SPKI fingerprint, one-time HMAC proof, TTL, nonces, session id, single-use state, and attempt rate limits. [CITED: https://ktor.io/docs/server-ssl.html] [CITED: https://square.github.io/okhttp/3.x/okhttp/okhttp3/CertificatePinner.html] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] |
| TRAN-06 | Android and desktop maintain a reliable control channel for pairing state, heartbeat, diagnostics, profile metadata, and haptic commands. [VERIFIED: .planning/REQUIREMENTS.md] | Use versioned JSON control envelopes over WebSocket; implement heartbeat, session lifecycle, diagnostics, minimal profile metadata, and reserved haptic envelope type only. [CITED: https://ktor.io/docs/server-websockets.html] [CITED: https://kotlinlang.org/docs/serialization.html] [VERIFIED: .planning/phases/03-lan-pairing-and-secure-session/03-CONTEXT.md] |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- Chat responses use `$caveman ultra`; project artifacts use normal clear technical markdown when needed. [VERIFIED: AGENTS.md]
- Desktop support must remain Windows 11 x64 and macOS Apple Silicon in v1; protocol and virtual-controller assumptions cannot be single-platform. [VERIFIED: AGENTS.md]
- Android-to-desktop v1 transport is Wi-Fi/LAN with QR or pairing code. [VERIFIED: AGENTS.md]
- Visualizer path target is under 50 ms, but Phase 3 must not implement high-rate input metrics. [VERIFIED: AGENTS.md] [VERIFIED: 03-CONTEXT.md]
- Desktop profiles own final aim mapping; Android sends normalized gyro/raw aim data later. [VERIFIED: AGENTS.md]
- Direct desktop-to-gun Bluetooth is out of v1 scope. [VERIFIED: AGENTS.md]
- Existing code has no broad source conventions beyond following local patterns; keep docs short, factual, and agent-facing. [VERIFIED: AGENTS.md]
- Current source tree has Android host code only plus planning/docs/fixtures; desktop source tree does not exist yet. [VERIFIED: rg --files]

## Summary

Phase 3 should build a small authenticated pairing/session vertical slice, not the full transport pipeline. [VERIFIED: 03-CONTEXT.md] The recommended path is desktop-initiated WSS pairing: desktop opens a short-lived pairing session, shows QR/manual fallback, Android scans or enters endpoint/code, verifies the desktop certificate public-key fingerprint, proves possession of one-time material, then maintains a JSON WebSocket control channel. [CITED: https://ktor.io/docs/server-ssl.html] [CITED: https://ktor.io/docs/server-websockets.html]

Use platform TLS/JCA/HMAC and certificate pinning instead of custom encryption. [CITED: https://www.rfc-editor.org/info/rfc8446/] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] [CITED: https://square.github.io/okhttp/3.x/okhttp/okhttp3/CertificatePinner.html] Persist only durable trust anchors and metadata: desktop public-key fingerprint, display name, last endpoint, and last-seen time. [VERIFIED: 03-CONTEXT.md] Do not persist one-time pairing secrets. [ASSUMED]

**Primary recommendation:** Implement Phase 3 as a Kotlin/JVM desktop companion using Ktor WSS plus a native Android OkHttp WSS client, QR via Google Code Scanner on Android and ZXing on desktop, JSON control envelopes via kotlinx.serialization, and trust anchored to desktop SPKI SHA-256 fingerprint. [CITED: https://ktor.io/docs/server-dependencies.html] [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner] [CITED: https://zxing.github.io/zxing/dependency-info.html] [CITED: https://kotlinlang.org/docs/serialization.html]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Pairing session creation | Desktop companion | Android client | Desktop starts the session, owns listening endpoint, expiry, one-time material, QR/manual display, and rate limits. [VERIFIED: 03-CONTEXT.md] |
| QR scanning | Android app | Desktop companion | Android scans; desktop generates QR content. [VERIFIED: 03-CONTEXT.md] |
| Manual fallback | Android app + Desktop companion | — | Desktop displays endpoint/code/fingerprint; Android enters and validates them. [VERIFIED: 03-CONTEXT.md] |
| Encrypted reliable control | Desktop companion + Android service | Platform TLS/JCA | Both endpoints maintain WSS and heartbeat; TLS/JCA owns cryptographic primitives. [CITED: https://ktor.io/docs/server-ssl.html] [CITED: https://ktor.io/docs/server-websockets.html] |
| Trusted desktop identity | Android app storage | Desktop keystore | Android persists desktop public-key fingerprint; desktop persists its private key/certificate. [VERIFIED: 03-CONTEXT.md] [CITED: https://docs.oracle.com/javase/8/docs/api/java/security/KeyStore.html] |
| Session state UI | Android Activity + Desktop companion UI | HostSessionService state | Phase 2 UI already has Desktop link placeholder; Phase 3 should activate that surface while Packet stream remains pending. [VERIFIED: local code grep] |
| Haptic command support | Control envelope schema only | Phase 4 implementation | Phase 3 reserves type space but must not define payload, vibrate phone, or ack execution. [VERIFIED: 03-CONTEXT.md] |

## Standard Stack

### Core

| Library / API | Version | Purpose | Why Standard |
|---------------|---------|---------|--------------|
| Kotlin/JVM desktop companion | Kotlin plugin isolated from Android host; pin during implementation | Portable desktop pairing server and minimal UI | Existing project is Kotlin/Android; JVM desktop works on macOS Apple Silicon and Windows 11 x64 without platform driver assumptions. [VERIFIED: local Gradle] [ASSUMED] |
| Ktor Server Netty + WebSockets `io.ktor:ktor-server-netty-jvm`, `io.ktor:ktor-server-websockets-jvm`, `io.ktor:ktor-server-content-negotiation-jvm`, `io.ktor:ktor-serialization-kotlinx-json-jvm` | 3.5.0 current by Maven/Gradle Plugin search; package install must be human-verified because slopcheck unavailable. [ASSUMED] | Desktop HTTPS/WSS server and JSON WebSocket endpoint | Ktor docs define JVM-specific artifacts and WebSocket support for full-duplex sessions. [CITED: https://ktor.io/docs/server-dependencies.html] [CITED: https://ktor.io/docs/server-websockets.html] |
| OkHttp `com.squareup.okhttp3:okhttp` | 5.3.2 current by Maven search; package install must be human-verified because slopcheck unavailable. [ASSUMED] | Android WSS client and certificate pinning | OkHttp official docs cover Android/JVM client use, WebSocket support, BOM, and `CertificatePinner`. [CITED: https://square.github.io/okhttp/] [CITED: https://square.github.io/okhttp/3.x/okhttp/okhttp3/CertificatePinner.html] |
| Google Play services Code Scanner `com.google.android.gms:play-services-code-scanner` | 16.1.0 per Google docs/search; package install must be human-verified because slopcheck unavailable. [ASSUMED] | Android QR scanning normal path | Google docs state Code Scanner scans without the app requesting camera permission by delegating to Google Play services. [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner] |
| ZXing `com.google.zxing:core` and `com.google.zxing:javase` | 3.5.4 current by Maven search; package install must be human-verified because slopcheck unavailable. [ASSUMED] | Desktop QR generation | ZXing official dependency page gives Maven coordinates and GitHub release/search shows active releases. [CITED: https://zxing.github.io/zxing/dependency-info.html] |
| kotlinx.serialization JSON `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 current by Maven metadata/search; package install must be human-verified because slopcheck unavailable. [ASSUMED] | Versioned QR/control-envelope serialization | Kotlin docs define kotlinx.serialization as compiler-plugin-backed serialization with JSON support. [CITED: https://kotlinlang.org/docs/serialization.html] |
| Java TLS/JCA APIs: `SSLContext`, `KeyStore`, `SecureRandom`, `Mac` | JDK 17 platform APIs | TLS, desktop key storage, random material, HMAC proof | Official Java APIs provide keystore, cryptographically strong RNG, and HMAC primitives; use them instead of handwritten crypto. [CITED: https://docs.oracle.com/javase/8/docs/api/java/security/KeyStore.html] [CITED: https://docs.oracle.com/javase/8/docs/api/java/security/SecureRandom.html] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] |

### Supporting

| Library / API | Version | Purpose | When to Use |
|---------------|---------|---------|-------------|
| Android `SharedPreferences` | Platform API | Store trusted desktop fingerprint/name/last endpoint | Use for non-secret trust metadata; existing `AimCalibrationStore` already uses `SharedPreferences`. [VERIFIED: local code grep] |
| Java Swing/AWT | JDK 17 platform API | Minimal desktop QR/manual fallback window | Use for Phase 3 desktop companion to avoid pulling a large UI framework before driver strategy exists. [ASSUMED] |
| OWASP WebSocket Security guidance | Current web cheat sheet | Origin/auth/rate-limit/input-validation checklist | Use to shape WebSocket handshake validation and limits. [CITED: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Ktor WSS | Raw TCP framing | Raw TCP can be smaller, but loses WebSocket tooling/debuggability and contradicts D-14 unless materially simpler. [VERIFIED: 03-CONTEXT.md] |
| Ktor WSS | Node.js `ws` desktop scaffold | Node is quick but adds a second ecosystem and weaker fit with later JVM/Kotlin Android protocol sharing. [ASSUMED] |
| Google Code Scanner | CameraX + ML Kit Barcode Scanning | CameraX/ML Kit gives custom camera UI and no Play-services dependency, but adds camera permission/UI complexity not needed for Phase 3 because manual fallback exists. [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner] [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/android] |
| WSS/TLS + HMAC proof | Custom Noise/AEAD session | Noise can be strong, but adds protocol/library complexity; WSS/TLS already supplies encrypted reliable channel and certificate identity. [CITED: https://www.rfc-editor.org/info/rfc8446/] [ASSUMED] |
| Android `SharedPreferences` | Jetpack DataStore | DataStore is cleaner for async config, but current app already uses `SharedPreferences` and stored trust metadata is small. [VERIFIED: local code grep] [ASSUMED] |

**Installation (planner must gate every external package with human verification because slopcheck was unavailable):**

```kotlin
// desktop-companion/build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.0")
    implementation("io.ktor:ktor-server-websockets-jvm:3.5.0")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")
}

// android-host/app/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
```

**Version verification:** Maven metadata curl from the shell was DNS/SSL-blocked for most artifacts; escalated curl verified `kotlinx-coroutines-core-jvm` metadata only, while web search and official docs verified current package coordinates for the recommended stack. [VERIFIED: shell output] [CITED: https://central.sonatype.com/] Planner must re-run Maven/Gradle dependency resolution before install. [ASSUMED]

## Package Legitimacy Audit

> slopcheck install was attempted and unavailable in this environment; all external packages below are tagged `[ASSUMED]`, and the planner must insert `checkpoint:human-verify` before adding each dependency. [VERIFIED: shell output]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `io.ktor:*` [ASSUMED] | Maven Central | Active Ktor 3.x line; 3.5.0 found by registry/search. [ASSUMED] | Not retrieved. [VERIFIED: shell limitation] | `github.com/ktorio/ktor` from official project docs/search. [CITED: https://ktor.io/docs/server-dependencies.html] | unavailable | Approved only after human verification |
| `com.squareup.okhttp3:okhttp` [ASSUMED] | Maven Central | 5.3.2 found by registry/search dated 2025-11-18. [ASSUMED] | Not retrieved. [VERIFIED: shell limitation] | `github.com/square/okhttp` from official docs/search. [CITED: https://square.github.io/okhttp/] | unavailable | Approved only after human verification |
| `com.google.android.gms:play-services-code-scanner` [ASSUMED] | Google Maven | 16.1.0 found by Google docs/search. [ASSUMED] | Not retrieved. [VERIFIED: shell limitation] | Google Play services / ML Kit docs. [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner] | unavailable | Approved only after human verification |
| `com.google.zxing:core` / `javase` [ASSUMED] | Maven Central | 3.5.4 found by registry/search dated 2025-11-11. [ASSUMED] | Not retrieved. [VERIFIED: shell limitation] | `github.com/zxing/zxing` from official ZXing docs/search. [CITED: https://zxing.github.io/zxing/dependency-info.html] | unavailable | Approved only after human verification |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` [ASSUMED] | Maven Central | 1.11.0 found by Maven metadata/search dated 2026-04-09. [ASSUMED] | Not retrieved. [VERIFIED: shell limitation] | `github.com/Kotlin/kotlinx.serialization` from Kotlin docs/search. [CITED: https://kotlinlang.org/docs/serialization.html] | unavailable | Approved only after human verification |

**Packages removed due to slopcheck [SLOP] verdict:** none; slopcheck was unavailable, so no verdicts were produced. [VERIFIED: shell output]  
**Packages flagged as suspicious [SUS]:** none by slopcheck; all packages require human verification because slopcheck was unavailable. [VERIFIED: shell output]

## Architecture Patterns

### System Architecture Diagram

```text
-------------------+       QR/manual endpoint + one-time material       +----------------------+
| Desktop Companion | --------------------------------------------------> | Android Host Activity |
| - pairing session |                                                     | - scan / manual entry |
| - QR/manual UI    | <-------------- WSS TLS + HMAC proof -------------- | - trust confirmation  |
| - TLS identity    |                                                     +----------+-----------+
+---------+---------+                                                                |
          |                                                                          |
          | accepted pair / heartbeat / diagnostics / profile metadata               |
          v                                                                          v
+-------------------+        versioned JSON control envelopes          +----------------------+
| Ktor WSS endpoint | <----------------------------------------------> | OkHttp WSS client    |
| - session registry|                                                 | - pinned fingerprint |
| - rate limits     |                                                 | - heartbeat state    |
+---------+---------+                                                 +----------+-----------+
          |                                                                          |
          v                                                                          v
+-------------------+                                                 +----------------------+
| Desktop trust/key |                                                 | Android trusted      |
| store (PKCS12)    |                                                 | desktops store       |
+-------------------+                                                 +----------------------+
```

### Recommended Project Structure

```text
android-host/
  app/src/main/java/com/btgun/host/session/     # Android trusted-desktop store, QR/manual parser, WSS client
  app/src/main/java/com/btgun/host/ui/          # Dashboard Desktop link state and pairing actions
  app/src/test/java/com/btgun/host/session/     # QR parser, trust-store, heartbeat, control-envelope tests

desktop-companion/
  build.gradle.kts                              # Kotlin/JVM app, Ktor, ZXing
  src/main/kotlin/com/btgun/desktop/pairing/    # Pairing session registry, QR payload, code fallback
  src/main/kotlin/com/btgun/desktop/control/    # WSS routing, control envelopes, heartbeat
  src/main/kotlin/com/btgun/desktop/security/   # TLS keystore, fingerprint, HMAC proof
  src/main/kotlin/com/btgun/desktop/ui/         # Minimal Swing pairing window
  src/test/kotlin/com/btgun/desktop/            # No-hardware pairing/session tests

docs/protocol/
  lan-pairing-v1.md                             # Pairing URI, proof transcript, control envelope spec
```

### Pattern 1: Desktop-Initiated Pairing Session

**What:** Desktop generates `session_id`, `desktop_nonce`, 128-bit+ QR secret, 6-digit manual code, expiry, and QR payload for one active pairing window. [VERIFIED: 03-CONTEXT.md] [ASSUMED]  
**When to use:** Every new trust establishment or explicit re-pair. [VERIFIED: 03-CONTEXT.md]  
**Example:**

```kotlin
// Source: Java SecureRandom docs; phase QR requirements.
data class PairingPayloadV1(
    val v: Int = 1,
    val sid: String,
    val host: String,
    val port: Int,
    val expiresAtEpochMillis: Long,
    val desktopSpkiSha256: String,
    val desktopNonce: String,
    val qrSecret: String,
)
```

### Pattern 2: WSS TLS Identity + One-Time HMAC Proof

**What:** Android pins the desktop SPKI fingerprint from QR/stored trust, connects over WSS, and sends a proof derived from one-time secret/code plus both nonces. [CITED: https://square.github.io/okhttp/3.x/okhttp/okhttp3/CertificatePinner.html] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html]  
**When to use:** Before any trusted control message is accepted. [VERIFIED: 03-CONTEXT.md]  
**Example:**

```kotlin
// Source: javax.crypto.Mac docs. Exact transcript constants must be versioned in docs/protocol/lan-pairing-v1.md.
fun pairingProof(secret: ByteArray, sid: String, desktopNonce: String, androidNonce: String, fingerprint: String): ByteArray {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"))
    val transcript = "btgun-pair-v1|$sid|$desktopNonce|$androidNonce|$fingerprint"
    return mac.doFinal(transcript.toByteArray(Charsets.UTF_8))
}
```

### Pattern 3: Versioned Control Envelope

**What:** All reliable-channel messages use `v`, `type`, `msg_id`, `session_id`, `seq`, `sent_elapsed_nanos`, and typed body. [ASSUMED]  
**When to use:** Pairing state, session ready, heartbeat ping/pong, diagnostics, minimal profile metadata, and haptic envelope reservation. [VERIFIED: 03-CONTEXT.md]  
**Example:**

```kotlin
// Source: Kotlin serialization docs.
@kotlinx.serialization.Serializable
data class ControlEnvelope(
    val v: Int = 1,
    val type: String,
    val msgId: String,
    val sessionId: String,
    val seq: Long,
    val sentElapsedNanos: Long,
    val body: kotlinx.serialization.json.JsonObject,
)
```

### Anti-Patterns to Avoid

- **Plain `ws://` control channel:** Accepts local network eavesdropping/tampering risk; use WSS with pinned desktop identity. [CITED: https://www.rfc-editor.org/info/rfc8446/] [VERIFIED: 03-CONTEXT.md]
- **Permanent pairing code:** Violates D-10 and D-11; use short TTL, single-use session, and rate limits. [VERIFIED: 03-CONTEXT.md]
- **IP/name as identity:** LAN addresses and display names are metadata; desktop public-key fingerprint is the durable trust anchor. [VERIFIED: 03-CONTEXT.md]
- **Haptic payload now:** Phase 3 reserves haptic type only; payload schema, Android vibration, and ack/fail semantics remain Phase 4. [VERIFIED: 03-CONTEXT.md]
- **Activating Packet stream UI:** Packet stream must remain inactive/pending Phase 4. [VERIFIED: 03-CONTEXT.md] [VERIFIED: local code grep]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebSocket protocol | Custom frame parser | Ktor server WebSockets + OkHttp client | WebSocket framing, lifecycle, ping/pong behavior, and close semantics have edge cases. [CITED: https://ktor.io/docs/server-websockets.html] [CITED: https://square.github.io/okhttp/] |
| Transport encryption | Custom AEAD/TCP wrapper | TLS/WSS with JDK/Android TLS stack | TLS is standardized for encrypted authenticated channels; custom crypto is high risk. [CITED: https://www.rfc-editor.org/info/rfc8446/] |
| QR scanning camera pipeline | Custom Camera2 scanner | Google Code Scanner | Official API avoids app camera permission and returns scan results through Google Play services. [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner] |
| QR generation | Hand-drawn QR matrix | ZXing QR writer | QR encoding has mode/error-correction details. [CITED: https://zxing.github.io/zxing/dependency-info.html] |
| HMAC implementation | Homemade hash concatenation | `javax.crypto.Mac` HmacSHA256 | JCA provides standard MAC implementation. [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] |
| Desktop private-key storage | Ad hoc PEM parser | Java `KeyStore` PKCS12/JKS | Java KeyStore is standard certificate/key storage; PKCS12 is default in modern Java. [CITED: https://docs.oracle.com/javase/8/docs/api/java/security/KeyStore.html] [CITED: https://openjdk.org/jeps/229] |

**Key insight:** Phase 3 security should combine standard TLS identity, a short-lived pairing proof, and strict state machines; custom encryption or custom WebSocket framing adds risk without helping requirements. [CITED: https://www.rfc-editor.org/info/rfc8446/] [VERIFIED: 03-CONTEXT.md]

## Common Pitfalls

### Pitfall 1: Treating LAN as Trusted
**What goes wrong:** Another LAN device injects control messages or captures pairing material. [ASSUMED]  
**Why it happens:** Local Wi-Fi feels private, but project research already says plaintext LAN is unsafe. [VERIFIED: .planning/research/PITFALLS.md]  
**How to avoid:** Use WSS, pinned desktop fingerprint, one-time proof, TTL, and sequence/replay checks for control messages. [CITED: https://www.rfc-editor.org/info/rfc8446/] [VERIFIED: 03-CONTEXT.md]  
**Warning signs:** Code accepts `ws://`, accepts control frames before pair proof, or stores pairing secret after success. [ASSUMED]

### Pitfall 2: Manual Fallback Becomes Primary UX
**What goes wrong:** Users must type IP/port on normal path. [VERIFIED: 03-CONTEXT.md]  
**Why it happens:** Manual path is easier than QR scanner integration. [ASSUMED]  
**How to avoid:** Put scan action first, manual entry visible but secondary, and test QR path as acceptance path. [VERIFIED: 03-CONTEXT.md]  
**Warning signs:** TRAN-02 test only covers manual IP entry. [VERIFIED: .planning/REQUIREMENTS.md]

### Pitfall 3: Trust Anchor Drift
**What goes wrong:** Android silently updates trusted fingerprint when IP/name changes and accepts an impersonator. [VERIFIED: 03-CONTEXT.md]  
**Why it happens:** Endpoint metadata is confused with identity. [VERIFIED: 03-CONTEXT.md]  
**How to avoid:** Store fingerprint separately, fail closed on mismatch, show explicit trust problem with old/new fingerprint. [VERIFIED: 03-CONTEXT.md]  
**Warning signs:** Store key is host/IP, not fingerprint; reconnect overwrites existing record. [ASSUMED]

### Pitfall 4: Haptic Scope Creep
**What goes wrong:** Phase 3 starts sending haptic payloads or vibrating phone, forcing Phase 4 decisions early. [VERIFIED: 03-CONTEXT.md]  
**Why it happens:** TRAN-06 includes haptic commands, but context narrows Phase 3 to envelope reservation. [VERIFIED: 03-CONTEXT.md]  
**How to avoid:** Add `reserved.haptic_command` type with `body` rejected or ignored until Phase 4; document no ack/fail semantics yet. [VERIFIED: 03-CONTEXT.md]  
**Warning signs:** Code contains `duration`, `strength`, `ttl`, `Vibrator.vibrate`, or haptic ack/fail on remote command. [VERIFIED: 03-CONTEXT.md]

### Pitfall 5: Ktor/Kotlin Version Drift
**What goes wrong:** New desktop Gradle dependencies force Android Kotlin/AGP upgrades during a pairing phase. [ASSUMED]  
**Why it happens:** Ktor latest versions may track newer Kotlin than existing Android host Kotlin 2.0.21. [VERIFIED: local Gradle] [ASSUMED]  
**How to avoid:** Start desktop companion as an isolated Gradle build or verify Kotlin/AGP compatibility before sharing binary modules. [ASSUMED]  
**Warning signs:** Planner upgrades Android Gradle Plugin or Kotlin plugin without a specific pairing need. [VERIFIED: local Gradle]

## Code Examples

Verified patterns from official/local sources:

### Android WSS Client Boundary

```kotlin
// Source: OkHttp docs. Planner must verify OkHttp 5 API before implementation.
class DesktopControlClient(
    private val client: okhttp3.OkHttpClient,
    private val clock: () -> Long,
) {
    fun connect(url: String, listener: okhttp3.WebSocketListener): okhttp3.WebSocket {
        val request = okhttp3.Request.Builder().url(url).build()
        return client.newWebSocket(request, listener)
    }
}
```

### Control State Machine

```kotlin
// Source: Phase 3 context. Keep control state separate from Phase 4 packet stream.
enum class DesktopLinkPhase {
    IDLE,
    SCANNING_QR,
    CONNECTING,
    PAIRING_PROOF,
    CONNECTED,
    DEGRADED,
    DISCONNECTED,
    TRUST_PROBLEM,
}
```

### Heartbeat Timing

```kotlin
// Source: Phase 3 context. Intervals are planner defaults, not protocol law.
data class HeartbeatPolicy(
    val sendEveryMillis: Long = 1_000,
    val degradedAfterMillis: Long = 3_000,
    val disconnectedAfterMillis: Long = 8_000,
)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual IP-only LAN pairing | QR primary with visible manual fallback | Locked in Phase 3 context on 2026-06-07. [VERIFIED: 03-CONTEXT.md] | Planner must make QR path first-class and manual path secondary. [VERIFIED: 03-CONTEXT.md] |
| Plain local WebSocket | WSS/TLS plus one-time proof and pinned fingerprint | Phase 3 security decision. [VERIFIED: 03-CONTEXT.md] | Control channel accepts no trusted messages before authenticated encrypted session. [VERIFIED: 03-CONTEXT.md] |
| Discovery-first LAN setup | QR-provided endpoint for v1 | Phase 3 D-05. [VERIFIED: 03-CONTEXT.md] | Do not add NSD/mDNS tasks for normal v1 pairing. [VERIFIED: 03-CONTEXT.md] |
| Haptic command implementation in control channel | Reserved haptic envelope only | Phase 3 D-16. [VERIFIED: 03-CONTEXT.md] | Phase 4 owns payload, execution, and ack/fail semantics. [VERIFIED: 03-CONTEXT.md] |

**Deprecated/outdated:**
- Plaintext LAN input/control is not acceptable for this project. [VERIFIED: .planning/research/PITFALLS.md]
- Manual IP entry as normal path is out of scope for the product path. [VERIFIED: 03-CONTEXT.md]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Kotlin/JVM desktop companion with Swing is the smallest portable Phase 3 desktop surface. | Standard Stack | Planner may choose a different desktop UI stack if packaging or later driver integration needs it. |
| A2 | WSS/TLS plus pinned desktop fingerprint and HMAC proof is sufficient for Phase 3 authenticated encrypted reliable control. | Summary / Patterns | If future UDP key derivation needs stronger shared-key agreement now, plan must add explicit HKDF/session key contract. |
| A3 | Google Play services Code Scanner is acceptable for target Android device; manual fallback covers no-Play-services cases. | Standard Stack | If device lacks Google Play services and QR remains mandatory, planner must choose CameraX + ML Kit bundled scanner instead. |
| A4 | OkHttp 5.3.2, Ktor 3.5.0, and kotlinx.serialization 1.11.0 can be introduced without forcing Android Kotlin/AGP upgrade. | Standard Stack | If incompatible, planner must pin older compatible versions or isolate desktop dependencies. |
| A5 | `SharedPreferences` is sufficient for trusted desktop metadata because no long-term secret is stored on Android. | Standard Stack | If Android later stores private keys/session secrets, planner must move to Android Keystore-backed storage. |

## Open Questions (RESOLVED)

1. **Desktop companion Gradle shape**
   - Resolution: Phase 3 uses an independent `desktop-companion` Gradle build with its own `settings.gradle.kts`, JVM 17 target, application entry point, and main-style test registration. [VERIFIED: 03-01-PLAN.md]
   - Why: No desktop source tree exists, and an isolated build avoids forcing Android Gradle Plugin or Kotlin plugin changes while still producing a runnable macOS/Windows-portable pairing companion. [VERIFIED: rg --files] [VERIFIED: 03-PATTERNS.md]
   - Planner requirement: Do not convert the repository to a root multi-project build during Phase 3 unless dependency resolution proves the isolated build cannot run; if that happens, keep Android and desktop dependency versions pinned separately and preserve existing plan IDs/waves. [ASSUMED]

2. **First-run desktop certificate generation**
   - Resolution: Implement `DesktopIdentityStore.loadOrCreateIdentity()` in app code. It loads an existing PKCS12 keystore when present, otherwise creates a self-signed desktop TLS identity on first run, stores it locally, and exposes a stable SPKI SHA-256 fingerprint for QR/manual payloads and Android pinning. [VERIFIED: 03-01-PLAN.md] [VERIFIED: 03-03-PLAN.md]
   - Why: Ktor supports SSL from Java KeyStore, while requiring `keytool` as a manual dev setup step would make first-run pairing brittle and platform-specific. [CITED: https://ktor.io/docs/server-ssl.html] [CITED: https://docs.oracle.com/javase/8/docs/api/java/security/KeyStore.html]
   - Planner requirement: Tests must verify stable fingerprint output across reloads and must not log or persist private key material outside the keystore. [VERIFIED: 03-01-PLAN.md] [VERIFIED: 03-VALIDATION.md]

3. **Google Play services availability / QR scanner fallback**
   - Resolution: Android uses Google Play services Code Scanner for the normal QR path when available, and the visible manual fallback is the required no-Play-services fallback for Phase 3. [VERIFIED: 03-05-PLAN.md] [VERIFIED: 03-UI-SPEC.md]
   - Why: Code Scanner matches the lightweight Android UI goal and avoids app camera permission handling; Phase 3 already requires visible manual endpoint plus 6-digit code, so a device without compatible Play services can still pair without LAN discovery or manual IP entry as the primary tested QR path. [CITED: https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner] [VERIFIED: 03-CONTEXT.md]
   - Planner requirement: `MainActivity` must detect scanner unavailability or scan failure, show clear scanner-unavailable/rescan/manual-entry state, and preserve the manual fallback. Do not add CameraX/ML Kit bundled scanner in Phase 3; add it only through a targeted plan revision if physical-device QR smoke proves Google Code Scanner unavailable and manual fallback is insufficient for acceptance. [VERIFIED: 03-05-PLAN.md] [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| JDK | Android Gradle and desktop companion | yes | OpenJDK 17.0.19. [VERIFIED: shell `java -version`] | none needed |
| Gradle | Android tests and new desktop build | yes with escalation | Gradle task listing worked when sandbox escalation allowed; non-escalated failed on socket/native-service limits. [VERIFIED: shell output] | Run Gradle with approved escalation in this environment. [VERIFIED: shell output] |
| Android SDK / adb | Android app install and QR/device testing | partial | `adb` 36.0.0 available; `sdkmanager` not on PATH. [VERIFIED: shell output] | Use existing SDK path; install missing SDK components manually if needed. [ASSUMED] |
| Node/npm | GSD tooling / optional scripts | yes | Node v22.12.0, npm 10.9.0. [VERIFIED: shell output] | none needed |
| Context7 CLI | Library docs lookup fallback | no | `ctx7 not found`. [VERIFIED: shell output] | Official docs and web search used. [VERIFIED: shell output] |
| slopcheck | Package legitimacy gate | no | install attempted; command unavailable after attempt. [VERIFIED: shell output] | Gate all external packages behind human verification. [VERIFIED: shell output] |
| Google Play services on Android test device | QR scanning with Code Scanner | unknown | not probed. [ASSUMED] | Manual fallback; CameraX/ML Kit if QR must work without Play services. [ASSUMED] |

**Missing dependencies with no fallback:** none found for planning. [VERIFIED: shell output]  
**Missing dependencies with fallback:** Context7 CLI, slopcheck, Google Play services availability. [VERIFIED: shell output] [ASSUMED]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Existing Android unit tests are executable Kotlin/Java `main()` test classes under Gradle `testDebugUnitTest`; desktop framework not created yet. [VERIFIED: local Gradle/code] |
| Config file | `android-host/app/build.gradle.kts` custom `tasks.withType<Test>()` runner. [VERIFIED: local file] |
| Quick run command | `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest --tests '*DashboardState*'` [VERIFIED: local Gradle shape] |
| Full suite command | `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/lucas.rancez/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p android-host testDebugUnitTest` [VERIFIED: shell PASS with escalation] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| TRAN-01 | Desktop creates pairing session with QR payload, manual code, TTL, best IPv4 endpoint, and QR image. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `gradle -p desktop-companion test --tests '*PairingSession*'` [ASSUMED] | no - Wave 0 |
| TRAN-02 | Android parses QR/manual payload and surfaces scan/manual errors without LAN discovery. [VERIFIED: 03-CONTEXT.md] | unit + manual device QR smoke | `gradle -p android-host testDebugUnitTest --tests '*PairingPayload*'` [ASSUMED] | no - Wave 0 |
| TRAN-03 | Pairing proof rejects expired, reused, wrong-code, wrong-fingerprint, and replayed nonce attempts. [VERIFIED: .planning/REQUIREMENTS.md] | unit + integration | `gradle -p desktop-companion test --tests '*PairingSecurity*'` [ASSUMED] | no - Wave 0 |
| TRAN-06 | WSS control channel sends ready, heartbeat ping/pong, diagnostics, minimal profile metadata, and reserved haptic envelope type only. [VERIFIED: .planning/REQUIREMENTS.md] | unit + local integration | `gradle -p desktop-companion test --tests '*ControlChannel*'` and Android control client tests. [ASSUMED] | no - Wave 0 |

### Sampling Rate

- **Per task commit:** focused unit tests for changed Android or desktop module. [ASSUMED]
- **Per wave merge:** full Android `testDebugUnitTest` plus desktop `test`. [ASSUMED]
- **Phase gate:** local desktop companion starts, Android can scan/enter pairing data on device, WSS connects with fingerprint proof, heartbeat reaches connected/degraded/disconnected states, and Packet stream remains Phase 4 pending. [VERIFIED: 03-CONTEXT.md]

### Wave 0 Gaps

- [ ] `desktop-companion/build.gradle.kts` and `desktop-companion/src/test/...` test harness. [VERIFIED: rg --files]
- [ ] `android-host/app/src/test/java/com/btgun/host/session/PairingPayloadTest.kt` for QR/manual parsing. [ASSUMED]
- [ ] `android-host/app/src/test/java/com/btgun/host/session/TrustedDesktopStoreTest.kt` for fingerprint mismatch behavior. [ASSUMED]
- [ ] `desktop-companion/src/test/kotlin/.../PairingSecurityTest.kt` for TTL, rate limit, single-use, replay checks. [ASSUMED]
- [ ] `docs/protocol/lan-pairing-v1.md` checked by tests or fixture samples. [ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | One-time QR secret or 6-digit code bound to pairing session, short TTL, HMAC proof, rate limiting. [VERIFIED: 03-CONTEXT.md] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] |
| V3 Session Management | yes | Session id, nonce transcript, single-use pairing material, heartbeat, timeout, disconnect states. [VERIFIED: 03-CONTEXT.md] |
| V4 Access Control | yes | Reject control frames before authenticated session and reject mismatched desktop fingerprint. [VERIFIED: 03-CONTEXT.md] |
| V5 Input Validation | yes | Strict JSON schema, version checks, max message sizes, known `type` allowlist. [CITED: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html] [ASSUMED] |
| V6 Cryptography | yes | TLS/WSS, Java/Android KeyStore/TLS/JCA, `SecureRandom`, `Mac`; no custom crypto primitives. [CITED: https://www.rfc-editor.org/info/rfc8446/] [CITED: https://docs.oracle.com/javase/8/docs/api/java/security/SecureRandom.html] |

### Known Threat Patterns for LAN Pairing

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Rogue desktop impersonates trusted host | Spoofing | Persist and pin desktop public-key fingerprint; fail closed on mismatch. [VERIFIED: 03-CONTEXT.md] |
| Pairing code brute force | Spoofing | 6-digit code only with short TTL, per-session attempt limit, and backoff/lockout. [VERIFIED: 03-CONTEXT.md] |
| Replay of pair request | Replay/Tampering | Nonces, session id, HMAC transcript, single-use session material. [VERIFIED: .planning/REQUIREMENTS.md] [CITED: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html] |
| Control message injection before auth | Tampering | No trusted control handling until WSS is established and pair proof passes. [VERIFIED: 03-CONTEXT.md] |
| Oversized WebSocket messages | Denial of Service | Set max frame/message size and reject unknown envelope versions/types. [CITED: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html] |
| Secret leakage in logs/QR screenshots | Information Disclosure | Redact QR secret/code/proof in logs; show only fingerprint suffix and session state in diagnostics. [ASSUMED] |

## Sources

### Primary (HIGH confidence)
- `AGENTS.md` - project constraints, stack, architecture, GSD workflow. [VERIFIED: local]
- `.planning/phases/03-lan-pairing-and-secure-session/03-CONTEXT.md` - locked Phase 3 decisions and scope. [VERIFIED: local]
- `.planning/REQUIREMENTS.md` - TRAN-01, TRAN-02, TRAN-03, TRAN-06. [VERIFIED: local]
- `.planning/STATE.md` - Phase 3 status and haptic boundary concern. [VERIFIED: local]
- `android-host/app/src/main/java/com/btgun/host/...` - existing DashboardState, MainActivity, HostSessionService, PermissionGate, SharedPreferences pattern. [VERIFIED: local]
- https://ktor.io/docs/server-websockets.html - Ktor WebSocket support. [CITED]
- https://ktor.io/docs/server-ssl.html - Ktor SSL/keystore support. [CITED]
- https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner - Google Code Scanner behavior and dependency. [CITED]
- https://square.github.io/okhttp/ - OkHttp overview and release guidance. [CITED]
- https://square.github.io/okhttp/3.x/okhttp/okhttp3/CertificatePinner.html - certificate pinning semantics. [CITED]
- https://kotlinlang.org/docs/serialization.html - kotlinx.serialization plugin/runtime. [CITED]
- https://zxing.github.io/zxing/dependency-info.html - ZXing Maven coordinates. [CITED]
- https://www.rfc-editor.org/info/rfc8446/ - TLS 1.3 security purpose. [CITED]
- https://www.rfc-editor.org/info/rfc5869/ - HKDF reference for possible Phase 4 key derivation. [CITED]

### Secondary (MEDIUM confidence)
- Maven Central / MvnRepository search results for current package versions because shell network verification was partially blocked. [ASSUMED]
- OWASP WebSocket Security Cheat Sheet for WebSocket validation checklist. [CITED: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html]

### Tertiary (LOW confidence)
- Swing as minimal desktop UI scaffold and exact heartbeat timeout defaults. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM - official docs support core APIs, but slopcheck unavailable and Maven shell verification partially failed. [VERIFIED: shell output]
- Architecture: HIGH - Phase 3 decisions tightly constrain QR/manual/WSS/trust/control boundaries. [VERIFIED: 03-CONTEXT.md]
- Pitfalls: HIGH - security and scope pitfalls map directly to locked decisions and prior project research. [VERIFIED: 03-CONTEXT.md] [VERIFIED: .planning/research/PITFALLS.md]

**Research date:** 2026-06-07  
**Valid until:** 2026-07-07 for architecture decisions; package versions should be refreshed before install. [ASSUMED]
