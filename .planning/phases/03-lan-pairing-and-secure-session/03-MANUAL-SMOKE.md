# Phase 03 Manual Smoke Guide

Purpose: verify the implemented LAN pairing/control behavior on a physical Android device and the desktop companion before Phase 4 transport work starts.

## Preconditions

- Desktop and Android device are on the same local LAN.
- Android host app is installed with current Phase 3 code.
- Android Bluetooth/location/sensor/LAN capability prompts are granted enough to start the host session.
- Desktop companion tests and Android unit tests have passed before manual smoke.
- No Phase 4 packet stream or phone haptic command execution is expected in this guide.

## Start Desktop Pairing

1. On desktop, start the companion:

   ```bash
   JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/bt-gun-gradle-home gradle -p desktop-companion run
   ```

2. In `BT Gun Desktop`, click `Start pairing`.
3. Confirm the desktop shows:
   - QR code as the primary path.
   - Active LAN endpoint host and port.
   - Visible `Manual fallback` data: endpoint, port, 6-digit code, and fingerprint suffix.
   - Expiry countdown.
   - Session state such as `pairing ready`.

Expected: QR secret, full proof, and private key material are not visible.

## QR Normal Path

1. On Android, start or keep a live host session active.
2. Tap `Scan desktop QR`.
3. Scan the QR shown by the desktop companion.
4. Watch the Android `Desktop link` row.

Expected:

- Android leaves scanning state and moves through connecting/proving identity toward connected.
- The visible desktop fingerprint suffix matches the desktop window suffix.
- First trusted desktop metadata is saved after successful QR-derived connection.
- Desktop state moves from pairing ready/android connected to authenticated or connected state.
- `Packet stream` remains `Not built yet. Pending Phase 4.` or otherwise inactive.
- No phone haptic command is executed from the desktop. Haptic commands are reserved for Phase 4.

## Manual Fallback

1. Start a fresh desktop pairing window if the previous one was consumed.
2. On Android, tap `Enter manually`.
3. Enter the desktop host/IP, port, 6-digit code, fingerprint suffix, and session id shown by desktop.
4. Tap the manual pair action.

Expected:

- Manual fallback is visible next to QR scan and usable without scanning.
- Valid manual values move Android into connecting/proving identity state.
- Manual path does not trigger LAN discovery or any broad search.
- If only a fingerprint suffix is available and no trusted desktop row exists, Android blocks with a trust problem and tells the user to scan QR first.

## Wrong Code and Rate Limit

1. Start a fresh desktop pairing window.
2. On Android manual entry, use the correct host/port/session id/fingerprint suffix but intentionally enter an incorrect 6-digit code.
3. Repeat failed attempts until desktop reports its rate-limit state.

Expected:

- Wrong code is rejected before trusted control state exists.
- Android shows a clear control/pairing error.
- Desktop reports `rate_limited` after the configured failed-attempt limit.
- A locked session cannot be rescued by entering the correct code; restart pairing.

## Expired QR

1. Start desktop pairing.
2. Wait until the desktop expiry countdown reaches expired, or restart desktop pairing and keep an old QR screenshot.
3. Scan the expired QR on Android.

Expected:

- Android rejects the QR as expired.
- Android copy instructs rescan or manual edit.
- Android does not silently broaden into LAN discovery.
- Desktop does not create trusted control state from expired material.

## Trust Mismatch

1. Pair successfully once by QR so Android stores a trusted desktop.
2. Change desktop identity by using a different desktop identity store or clearing/regenerating desktop key material.
3. Start desktop pairing again with the changed identity.
4. Scan the new QR from Android.

Expected:

- Android blocks with `Desktop identity changed` / trust problem copy.
- Stored old fingerprint is not silently overwritten.
- User must explicitly re-pair/trust the new identity before it can replace stored metadata.
- Desktop and Android do not accept trusted control messages for the mismatch.

## Heartbeat Degradation

1. Pair successfully and confirm Android shows connected desktop link state.
2. Interrupt the desktop companion, close the desktop window, or temporarily break LAN reachability.
3. Watch Android and desktop state transitions.

Expected:

- Connected state becomes degraded after heartbeat staleness.
- Degraded state becomes disconnected after the disconnected timeout.
- Android shows heartbeat age and last control error only for the control channel.
- `Packet stream` remains inactive throughout.
- Restarting desktop does not silently reconnect as the primary path.

## Trusted Desktop Reconnect

1. Pair successfully by QR once.
2. Stop desktop control or restart the Android app.
3. Start desktop companion again on the same trusted identity and endpoint.
4. On Android, tap `Use trusted desktop`.

Expected:

- `Use trusted desktop` is visible only after stored trusted metadata exists.
- Reconnect requires explicit tap; no silent auto-reconnect is the primary behavior.
- Android pins the stored fingerprint and reports trust problem if identity changed.
- Connected heartbeat state returns after successful explicit reconnect.
- `Packet stream` remains inactive.

## Closeout Checks

- QR path works without manual IP entry in the normal path.
- Manual fallback is visible and gives clear rejected-code and expired-session errors.
- Trust mismatch fails closed.
- Heartbeat states distinguish connected, degraded, and disconnected.
- Trusted desktop reconnect is explicit.
- No LAN discovery fallback is used.
- Packet stream is inactive.
- Haptic commands remain reserved for Phase 4.
