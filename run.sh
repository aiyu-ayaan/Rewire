#!/usr/bin/env bash
set -e

# Rewire Runner Script
# Builds, installs, and launches the app on a connected Android device or emulator.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

PACKAGE_NAME="com.rewire.app.debug"
ACTIVITY_NAME="com.rewire.app.MainActivity"

echo "==> Checking for connected Android devices..."
if ! command -v adb &> /dev/null; then
    if [ -d "$HOME/Android/Sdk/platform-tools" ]; then
        export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
    fi
fi

if ! command -v adb &> /dev/null; then
    echo "Error: adb command not found. Please install Android platform-tools or add it to PATH."
    exit 1
fi

DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "Error: No connected Android device or running emulator found."
    echo "Please start an emulator or connect a device with USB debugging enabled."
    exit 1
fi

echo "==> Device detected. Building and installing debug build..."
./gradlew installDebug

echo "==> Launching $PACKAGE_NAME..."
adb shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

echo "==> App launched successfully!"

# Optional logcat streaming
if [ "$1" == "--logs" ] || [ "$1" == "-l" ]; then
    echo "==> Streaming logcat for $PACKAGE_NAME (Ctrl+C to stop)..."
    adb logcat -v color --pid="$(adb shell pidof -s $PACKAGE_NAME)"
fi
