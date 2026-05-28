#!/bin/bash
# YuriReader local build wrapper
# Usage:
#   bash build-check.sh          → Java compilation check only
#   bash build-check.sh assemble → Full APK build (arm64 only)
#
# Prerequisites (proot Ubuntu):
#   - JDK 21, Android SDK, NDK 29 installed
#   - qemu-user-static installed (for x86-64 binary emulation)
#   - libc6-amd64-cross installed (for x86-64 dynamic linker)
#   - aapt2-wrapper compiled at ~/aapt2-wrapper (arm64 ELF)
#
# This script automatically wraps all x86-64 binaries that Gradle/AGP
# cannot execute natively on arm64, including:
#   - SDK build-tools (aapt2, aapt, aidl, zipalign, etc.)
#   - SDK platform-tools (adb, etc.)
#   - AGP's Maven-downloaded aapt2 (extracted to Gradle cache on each build)

set -e

unset LD_PRELOAD
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
WRAPPER=/home/ubuntu/aapt2-wrapper
QEMU=/usr/bin/qemu-x86_64-static
LD_PREFIX=/usr/x86_64-linux-gnu
FALLBACK=/opt/android-sdk/aapt2-maven.real

cd /home/ubuntu/workspace/repos/personalReader

# ── Step 1: Wrap SDK build-tools & platform-tools x86-64 binaries ──
wrap_sdk_binaries() {
  echo "=== Wrapping SDK x86-64 binaries ==="
  for dir in "$ANDROID_HOME/build-tools/34.0.0" "$ANDROID_HOME/platform-tools"; do
    for bin in "$dir"/*; do
      [ -f "$bin" ] || continue
      # Skip shell scripts (like d8)
      read -r first_line < "$bin" 2>/dev/null || continue
      case "$first_line" in '#!'*) continue ;; esac
      # Check if ELF x86-64
      magic=$(od -A n -t x1 -j 18 -N 2 "$bin" 2>/dev/null | tr -d ' \n')
      if [ "$magic" = "3e00" ]; then
        if [ ! -f "${bin}.real" ]; then
          mv "$bin" "${bin}.real"
          cat > "$bin" << WRAPPER
#!/bin/bash
exec $QEMU -L $LD_PREFIX "\$0.real" "\$@"
WRAPPER
          chmod +x "$bin"
          echo "  Wrapped: $(basename $bin)"
        fi
      fi
    done
  done
}

# ── Step 2: Wrap AGP's Maven-downloaded aapt2 in Gradle cache ──
wrap_gradle_aapt2() {
  echo "=== Wrapping Gradle aapt2 binaries ==="
  local count=0
  find /root/.gradle/caches -name "aapt2" -type f 2>/dev/null | while read f; do
    local dir=$(dirname "$f")
    local name=$(basename "$dir")
    # Only process aapt2 version directories
    case "$name" in aapt2-*) ;; *) continue ;; esac
    
    # Check if it's already our wrapper (arm64 ELF)
    local magic=$(od -A n -t x1 -j 18 -N 2 "$f" 2>/dev/null | tr -d ' \n')
    if [ "$magic" = "b700" ]; then
      # x86-64 - needs wrapping
      if [ ! -f "${f}.real" ]; then
        mv "$f" "${f}.real"
        cp "$WRAPPER" "$f"
        chmod +x "$f"
        echo "  Wrapped: $f"
      fi
    elif [ "$magic" = "d700" ]; then
      # arm64 - already our wrapper, ensure .real exists
      if [ ! -f "${f}.real" ]; then
        echo "  Wrapper exists but .real missing: $f"
      fi
    fi
  done
}

# ── Step 3: Wrap the aapt2 inside the Maven jar (so future extracts get wrapper) ──
patch_aapt2_jar() {
  echo "=== Patching aapt2 Maven jar ==="
  local jar_dir="/root/.gradle/caches/modules-2/files-2.1/com.android.tools.build/aapt2"
  # Find the -linux.jar
  for jar in $(find "$jar_dir" -name "aapt2-*-linux.jar" 2>/dev/null); do
    # Check if already patched (contains aapt2.real)
    if jar tf "$jar" 2>/dev/null | grep -q "aapt2.real"; then
      echo "  Already patched: $jar"
      continue
    fi
    
    # No need to modify jar — the wrapper uses a fallback path
    # Install the Maven aapt2 original binary to a known fallback location
    local tmpdir=$(mktemp -d)
    ( cd "$tmpdir" && jar xf "$jar" aapt2 2>/dev/null && \
      magic=$(od -A n -t x1 -j 18 -N 2 aapt2 2>/dev/null | tr -d ' \n') && \
      if [ "$magic" = "3e00" ]; then \
        cp "aapt2" "$FALLBACK" && chmod +x "$FALLBACK" && echo "  Installed fallback to $FALLBACK"; \
      else \
        echo "  Skipped (not x86-64): $jar"; \
      fi \
    )
    rm -rf "$tmpdir"
  done
}

# ── Run all wrapping steps ──
wrap_sdk_binaries
patch_aapt2_jar
wrap_gradle_aapt2

echo ""

# ── Build ──
if [ "$1" = "assemble" ]; then
  echo "=== assembleFdroidDebug (full APK build) ==="
  ./gradlew assembleFdroidDebug --no-configuration-cache
  EXIT_CODE=$?
  if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "=== APK location ==="
    find . -path "*/outputs/apk/fdroid/debug/*.apk" -type f 2>/dev/null | while read f; do
      ls -lh "$f"
    done
  fi
else
  echo "=== compileFdroidDebugJavaWithJavac ==="
  ./gradlew compileFdroidDebugJavaWithJavac
  EXIT_CODE=$?
fi

echo "=== EXIT CODE: $EXIT_CODE ==="
exit $EXIT_CODE