#!/bin/bash
# YuriReader local compile verification wrapper
# Usage: bash build-check.sh
# Runs compileFdroidDebugJavaWithJavac in proot Ubuntu (Java-only, no NDK)
set -e

unset LD_PRELOAD
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
cd /home/ubuntu/workspace/repos/personalReader

echo "=== compileFdroidDebugJavaWithJavac ==="
./gradlew compileFdroidDebugJavaWithJavac
EXIT_CODE=$?
echo "=== EXIT CODE: $EXIT_CODE ==="
exit $EXIT_CODE