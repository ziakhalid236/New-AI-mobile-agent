#!/usr/bin/env bash
# Static install-readiness check for ArtemisAccessibilityHelper.apk.
#
# Catches the failures a device reports as a bare "app not installed":
# unsigned or v1-only APKs on Android 11+, unaligned archives, a manifest
# without a launcher entry, an empty DEX, and a manifest hash that no longer
# matches the APK.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

APK="ArtemisAccessibilityHelper.apk"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_ROOT" ] || [ ! -d "$SDK_ROOT" ]; then
    for candidate in "$HOME/Android/Sdk" /opt/android-sdk /usr/local/share/android-sdk; do
        if [ -n "$candidate" ] && [ -d "$candidate" ]; then
            SDK_ROOT="$candidate"
            break
        fi
    done
fi
if [ -z "$SDK_ROOT" ] || [ ! -d "$SDK_ROOT" ]; then
    echo "Error: Android SDK not found. Set ANDROID_HOME." >&2
    exit 1
fi

BUILD_TOOLS="$(find "$SDK_ROOT/build-tools" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)"
PYTHON_BIN="${PYTHON:-python3}"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[ -f "$APK" ] || fail "$APK is missing; run build_apk.sh first"

echo "-> Signature schemes"
VERIFY="$("$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK")"
echo "$VERIFY" | sed -n '2,6p'
echo "$VERIFY" | grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" \
    || fail "v2 signature missing: Android 11+ refuses to install targetSdk 30+ APKs signed with v1 only"
echo "$VERIFY" | grep -q "Verified using v3 scheme (APK Signature Scheme v3): true" \
    || fail "v3 signature missing"

echo "-> Zip alignment"
"$BUILD_TOOLS/zipalign" -c -v 4 "$APK" > /dev/null || fail "APK is not 4-byte aligned"

echo "-> Manifest"
BADGING="$("$BUILD_TOOLS/aapt2" dump badging "$APK")"
echo "$BADGING" | sed -n '1,4p'
echo "$BADGING" | grep -q "^launchable-activity:" \
    || fail "no launcher activity: the app would install with no icon to open"
echo "$BADGING" | grep -q "application: label='.*' icon='.*'" \
    || fail "no application icon declared"
echo "$BADGING" | grep -q "minSdkVersion:'24'" || fail "unexpected minSdkVersion"
echo "$BADGING" | grep -q "targetSdkVersion:'35'" || fail "unexpected targetSdkVersion"

echo "-> DEX"
CLASS_COUNT="$("$BUILD_TOOLS/dexdump" "$APK" 2>/dev/null | grep -c "Class descriptor" || true)"
echo "   classes: $CLASS_COUNT"
[ "$CLASS_COUNT" -ge 20 ] || fail "DEX looks empty (found $CLASS_COUNT classes)"
"$BUILD_TOOLS/dexdump" "$APK" 2>/dev/null | grep -q "Lcom/artemis/helper/ArtemisAccessibilityService;" \
    || fail "accessibility service class missing from DEX"

echo "-> helper_manifest.json"
"$PYTHON_BIN" - <<'PY' || fail "helper_manifest.json does not describe this APK"
import hashlib, json, sys

apk = open("ArtemisAccessibilityHelper.apk", "rb").read()
manifest = json.load(open("helper_manifest.json", encoding="utf-8"))
digest = hashlib.sha256(apk).hexdigest()
if manifest.get("sha256") != digest:
    sys.exit(f"sha256 mismatch: manifest {manifest.get('sha256')} vs apk {digest}")
print(f"   {manifest['package']} {manifest['version_name']} (code {manifest['version_code']})")
print(f"   sha256 {digest[:16]}... matches")
PY

echo "APK check passed."
