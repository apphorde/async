#!/usr/bin/env bash
set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IMAGE=${ANDROID_BUILD_IMAGE:-storage-reader-android-build}
KEYSTORE=${ANDROID_DEBUG_KEYSTORE_FILE:-$ROOT/reader-vault-debug.keystore}
KEYSTORE_PASSWORD=${ANDROID_DEBUG_KEYSTORE_PASSWORD:-android}
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
CHECKSUM="$ROOT/app/build/outputs/apk/debug/app-debug.apk.sha256"

if [ ! -f "$KEYSTORE" ]; then
    printf 'Missing signing keystore: %s\n' "$KEYSTORE" >&2
    exit 1
fi

docker build --pull --tag "$IMAGE" "$ROOT"
docker run --rm \
    --volume "$ROOT:/workspace" \
    --volume "$KEYSTORE:/run/secrets/reader-vault-debug.keystore:ro" \
    --workdir /workspace \
    --env ANDROID_DEBUG_KEYSTORE=/run/secrets/reader-vault-debug.keystore \
    --env ANDROID_DEBUG_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" \
    "$IMAGE" ./gradlew assembleDebug --no-daemon

test -s "$APK"
docker run --rm \
    --entrypoint /opt/android-sdk/build-tools/35.0.0/apksigner \
    --volume "$ROOT:/workspace:ro" \
    "$IMAGE" verify --verbose /workspace/app/build/outputs/apk/debug/app-debug.apk

sha256sum "$APK" | tee "$CHECKSUM"
printf 'APK: %s\nChecksum: %s\n' "$APK" "$CHECKSUM"
