#!/bin/bash
# Wrapper to run x86-64 aapt2 via QEMU user-mode emulation on ARM64
# Usage: symlink or set as android.aapt2Path in gradle.properties
exec /usr/bin/qemu-x86_64-static -L /usr/x86_64-linux-gnu /opt/android-sdk/build-tools/35.0.0/aapt2 "$@"