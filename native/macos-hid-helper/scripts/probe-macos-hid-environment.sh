#!/usr/bin/env bash
set -euo pipefail

section() {
  printf '\n[%s]\n' "$1"
}

run_or_note() {
  local label="$1"
  shift
  section "$label"
  if "$@" 2>&1; then
    return 0
  fi
  printf 'unavailable: command failed\n'
}

print_command_path() {
  local command_name="$1"
  section "command -v ${command_name}"
  if command -v "$command_name" >/dev/null 2>&1; then
    command -v "$command_name"
  else
    printf 'missing\n'
  fi
}

section "probe"
printf 'capture_id=phase7-toolchain-signing-audit\n'
printf 'sanitized=true\n'
printf 'redactions=signing_identity_hashes,private_key_paths,qr_manual_codes,proof_values,stream_keys,hmac_keys,bluetooth_addresses,device_ids,screenshots\n'

run_or_note "sw_vers" sw_vers
run_or_note "uname -m" uname -m
run_or_note "xcodebuild -version" xcodebuild -version
run_or_note "xcrun --show-sdk-path" xcrun --show-sdk-path
run_or_note "swift --version" swift --version

section "security find-identity -v -p codesigning"
if command -v security >/dev/null 2>&1; then
  security find-identity -v -p codesigning 2>&1 |
    sed -E 's/[A-Fa-f0-9]{40}/<redacted-signing-identity-hash>/g'
else
  printf 'missing\n'
fi

print_command_path "hidutil"
print_command_path "ioreg"
print_command_path "systemextensionsctl"

section "corehid gate labels"
printf 'corehid-pass\n'
printf 'corehid-compile-blocked\n'
printf 'corehid-runtime-blocked\n'
printf 'corehid-visibility-failed\n'
printf 'corehid-output-failed\n'
