#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${HELPER_DIR}/../.." && pwd)"
BUILD_ROOT="${BTGUN_MACOS_HID_BUILD_ROOT:-/private/tmp/btgun-macos-hid-helper}"
SIGN_IDENTITY="${BTGUN_MACOS_HID_SIGN_IDENTITY:--}"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="${BUILD_ROOT}/${RUN_ID}"
SWIFTPM_SCRATCH="${OUT_DIR}/swiftpm"
export CLANG_MODULE_CACHE_PATH="${OUT_DIR}/clang-module-cache"

mkdir -p "${OUT_DIR}" "${CLANG_MODULE_CACHE_PATH}"

emit_manifest_reason() {
  local gate="$1"
  local reason="$2"
  local resume="${3:-sudo xcode-select -s /Applications/Xcode.app/Contents/Developer}"
  printf 'MANIFEST_REASON gate=%s status=blocked sanitized=true reason="%s"\n' "$gate" "$reason"
  printf 'RESUME_COMMAND %s\n' "$resume"
}

sanitize_stream() {
  sed -E \
    -e 's/[A-Fa-f0-9]{40}/<redacted-signing-identity-hash>/g' \
    -e 's#/Users/[^[:space:]]+#<redacted-user-path>#g' \
    -e 's#-----BEGIN [^-]+ PRIVATE KEY-----#<redacted-private-key>#g'
}

printf '[phase7-corehid-build]\n'
printf 'out_dir=%s\n' "${OUT_DIR}"
printf 'sanitized=true\n'

"${SCRIPT_DIR}/probe-macos-hid-environment.sh" | sanitize_stream | tee "${OUT_DIR}/environment-probe.txt"

printf '\n[swift corehid import]\n'
if ! swift -e 'import CoreHID; print("corehid-import-ok")' >"${OUT_DIR}/corehid-import.out" 2>"${OUT_DIR}/corehid-import.err"; then
  sanitize_stream <"${OUT_DIR}/corehid-import.err"
  emit_manifest_reason "corehid-compile-blocked" "Swift cannot import CoreHID with the selected SDK/toolchain; select matching full Xcode or matching CLT."
  exit 20
fi
cat "${OUT_DIR}/corehid-import.out"

printf '\n[swift build]\n'
if ! swift build --package-path "${HELPER_DIR}" --scratch-path "${SWIFTPM_SCRATCH}" -c debug >"${OUT_DIR}/swift-build.out" 2>"${OUT_DIR}/swift-build.err"; then
  sanitize_stream <"${OUT_DIR}/swift-build.err"
  emit_manifest_reason "corehid-compile-blocked" "SwiftPM failed to compile the CoreHID helper with the selected SDK/toolchain."
  exit 20
fi
sanitize_stream <"${OUT_DIR}/swift-build.out"

BIN_DIR="$(swift build --package-path "${HELPER_DIR}" --scratch-path "${SWIFTPM_SCRATCH}" -c debug --show-bin-path)"
HELPER_BIN="${BIN_DIR}/BtGunMacosHidHelper"

printf '\n[codesign]\n'
if [ "${SIGN_IDENTITY}" = "-" ]; then
  printf 'sign_identity=adhoc\n'
else
  printf 'sign_identity=keychain-named\n'
fi
if ! codesign --force --sign "${SIGN_IDENTITY}" --entitlements "${HELPER_DIR}/Entitlements.plist" "${HELPER_BIN}" >"${OUT_DIR}/codesign.out" 2>"${OUT_DIR}/codesign.err"; then
  sanitize_stream <"${OUT_DIR}/codesign.err"
  emit_manifest_reason "corehid-runtime-blocked" "Signing with com.apple.developer.hid.virtual.device entitlement failed." "provide an entitlement-capable signing identity/provisioning profile for com.apple.developer.hid.virtual.device"
  exit 21
fi
sanitize_stream <"${OUT_DIR}/codesign.out"
codesign -d --entitlements - "${HELPER_BIN}" >"${OUT_DIR}/codesign-entitlements.out" 2>&1 || true
sanitize_stream <"${OUT_DIR}/codesign-entitlements.out"
if ! rg -q "com.apple.developer.hid.virtual.device" "${OUT_DIR}/codesign-entitlements.out"; then
  emit_manifest_reason "corehid-runtime-blocked" "Signed helper does not contain com.apple.developer.hid.virtual.device entitlement." "provide an entitlement-capable signing identity/provisioning profile for com.apple.developer.hid.virtual.device"
  exit 21
fi

printf '\n[helper probe]\n'
"${HELPER_BIN}" --probe --hold-seconds 8 >"${OUT_DIR}/helper-probe.out" 2>"${OUT_DIR}/helper-probe.err" &
HELPER_PID=$!
sleep 2

if ! kill -0 "${HELPER_PID}" >/dev/null 2>&1; then
  wait "${HELPER_PID}" || true
  sanitize_stream <"${OUT_DIR}/helper-probe.err"
  emit_manifest_reason "corehid-runtime-blocked" "CoreHID helper exited before enumeration; entitlement or runtime policy likely blocked virtual device creation." "provide an entitlement-capable signing identity/provisioning profile for com.apple.developer.hid.virtual.device, then rerun BTGUN_MACOS_HID_SIGN_IDENTITY=<identity> native/macos-hid-helper/scripts/build-corehid-helper.sh"
  exit 22
fi

sanitize_stream <"${OUT_DIR}/helper-probe.out"

printf '\n[hidutil list]\n'
if ! hidutil list --matching '{"VendorID":0x1209,"ProductID":0xB707}' >"${OUT_DIR}/hidutil-list.out" 2>"${OUT_DIR}/hidutil-list.err"; then
  sanitize_stream <"${OUT_DIR}/hidutil-list.err"
  kill "${HELPER_PID}" >/dev/null 2>&1 || true
  wait "${HELPER_PID}" >/dev/null 2>&1 || true
  emit_manifest_reason "corehid-visibility-failed" "hidutil failed while matching BT Gun Virtual Joystick."
  exit 23
fi
sanitize_stream <"${OUT_DIR}/hidutil-list.out"

if ! rg -q "BT Gun Virtual Joystick|0x1209|0xB707|4615|47111" "${OUT_DIR}/hidutil-list.out"; then
  kill "${HELPER_PID}" >/dev/null 2>&1 || true
  wait "${HELPER_PID}" >/dev/null 2>&1 || true
  emit_manifest_reason "corehid-visibility-failed" "hidutil did not show the BT Gun Virtual Joystick VID/PID while helper was running."
  exit 23
fi

printf '\n[ioreg IOHIDDevice sanitized]\n'
ioreg -r -c IOHIDDevice -l -w 0 2>"${OUT_DIR}/ioreg.err" |
  rg "BT Gun|VendorID|ProductID|PrimaryUsage|MaxInputReportSize|MaxOutputReportSize" |
  sanitize_stream | tee "${OUT_DIR}/ioreg-filtered.out" || true

wait "${HELPER_PID}" >/dev/null 2>&1 || true

printf '\nMANIFEST_REASON gate=corehid-pass status=pass sanitized=true reason="CoreHID helper compiled, ad-hoc signed, launched, and was visible through hidutil."\n'
printf 'HELPER_BIN %s\n' "${HELPER_BIN}"
printf 'hidutil list --matching %s\n' "'{\"VendorID\":0x1209,\"ProductID\":0xB707}'"
printf 'ioreg -r -c IOHIDDevice -l -w 0\n'
