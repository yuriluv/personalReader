#!/bin/bash
# Build MuPDF native libraries for YuriReader (arm64-v8a only)
# Run inside proot Ubuntu:
#   proot-distro login ubuntu --bind ~/:/home/ubuntu --shared-tmp -- bash /home/ubuntu/workspace/repos/personalReader/build-mupdf.sh
#
# Prerequisites (already set up):
#   - NDK 29.0.14206865 at /opt/android-sdk/ndk/29.0.14206865
#   - Build dependencies: make, gcc, etc.

set -e

export ANDROID_HOME=/opt/android-sdk
export NDK_VERSION=29.0.14206865
export MUPDF_VERSION=1.23.7
export NDK_BUILD="$ANDROID_HOME/ndk/$NDK_VERSION/ndk-build"
export ABI=arm64-v8a

BUILDER_DIR="/home/ubuntu/workspace/repos/personalReader/Builder"
MUPDF_DIR="$BUILDER_DIR/mupdf-$MUPDF_VERSION"
JNI_DIR="$BUILDER_DIR/jni"
OUTPUT_DIR="/home/ubuntu/workspace/repos/personalReader/app/src/main/jniLibs"

echo "=== MuPDF Native Library Builder (arm64-v8a only) ==="

# ── Step 1: Clone MuPDF if not already cloned ──
if [ ! -d "$MUPDF_DIR" ]; then
  echo "=== Cloning MuPDF $MUPDF_VERSION ==="
  cd "$BUILDER_DIR"
  git clone --recursive https://github.com/ArtifexSoftware/mupdf.git --branch "$MUPDF_VERSION" "$MUPDF_DIR"
else
  echo "=== MuPDF already cloned, skipping clone ==="
fi

# ── Step 2: Build MuPDF (make generate + make release) ──
if [ ! -f "$MUPDF_DIR/build/release/libmupdf.a" ]; then
  echo "=== Building MuPDF ==="
  cd "$MUPDF_DIR"
  make generate
  make release
  echo "=== MuPDF build complete ==="
else
  echo "=== MuPDF already built, skipping ==="
fi

# ── Step 3: Set up Librera JNI patches ──
echo "=== Setting up Librera JNI patches ==="
MUPDF_JAVA="$MUPDF_DIR/platform/librera"
mkdir -p "$MUPDF_JAVA"
rm -rf "$MUPDF_JAVA/jni"
cp -Rp "$JNI_DIR" "$MUPDF_JAVA/jni"

# Rename versioned Android.mk
if [ -f "$MUPDF_JAVA/jni/Android-$MUPDF_VERSION.mk" ]; then
  mv "$MUPDF_JAVA/jni/Android-$MUPDF_VERSION.mk" "$MUPDF_JAVA/jni/Android.mk"
  echo "  Renamed Android-$MUPDF_VERSION.mk -> Android.mk"
fi

# ── Step 4: Apply Librera patches to MuPDF source ──
echo "=== Applying Librera patches ==="
SRC="$JNI_DIR/~mupdf-$MUPDF_VERSION"
DEST="$MUPDF_DIR/source"

if [ -d "$SRC" ]; then
  cp -rpv "$SRC/css-apply.c"       "$DEST/html/css-apply.c"
  cp -rpv "$SRC/epub-doc.c"        "$DEST/html/epub-doc.c"
  cp -rpv "$SRC/html-layout.c"     "$DEST/html/html-layout.c"
  cp -rpv "$SRC/html-parse.c"      "$DEST/html/html-parse.c"
  cp -rpv "$SRC/mucbz.c"           "$DEST/cbz/mucbz.c"
  cp -rpv "$SRC/muimg.c"           "$DEST/cbz/muimg.c"
  cp -rpv "$SRC/load-webp.c"       "$DEST/fitz/load-webp.c"
  cp -rpv "$SRC/image.c"           "$DEST/fitz/image.c"
  cp -rpv "$SRC/unzip.c"            "$DEST/fitz/unzip.c"
  cp -rpv "$SRC/directory.c"        "$DEST/fitz/directory.c"
  cp -rpv "$SRC/xml.c"             "$DEST/fitz/xml.c"
  cp -rpv "$SRC/list-device.c"     "$DEST/fitz/list-device.c"
  cp -rpv "$SRC/pdf-xref.c"        "$DEST/pdf/pdf-xref.c"
  cp -rpv "$SRC/image-imp.h"       "$DEST/fitz/image-imp.h"
  cp -rpv "$SRC/compressed-buffer.h" "$MUPDF_DIR/include/mupdf/fitz/compressed-buffer.h"
  cp -rpv "$SRC/context.h"         "$MUPDF_DIR/include/mupdf/fitz/context.h"
  cp -rpv "$SRC/j2k.c"             "$MUPDF_DIR/thirdparty/openjpeg/src/lib/openjp2/j2k.c"
  cp -rpv "$SRC/pi.c"              "$MUPDF_DIR/thirdparty/openjpeg/src/lib/openjp2/pi.c"
  echo "  Applied Librera patches to MuPDF"
else
  echo "  No custom patches directory found ($SRC), skipping"
fi

# ── Step 5: Build JNI libraries with NDK (arm64-v8a only) ──
echo "=== Building JNI libraries for $ABI ==="
cd "$MUPDF_JAVA"
"$NDK_BUILD" NDK_PROJECT_PATH=. NDK_APPLICATION_MK=jni/Application.mk APP_ABI=$ABI APP_PLATFORM=android-24

# ── Step 6: Copy .so files to jniLibs ──
echo "=== Copying .so files to jniLibs ==="
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/$ABI"
cp -rp "$MUPDF_JAVA/libs/$ABI/"*.so "$OUTPUT_DIR/$ABI/"

echo "=== .so files ==="
find "$OUTPUT_DIR" -name "*.so" -exec ls -lh {} \;

echo ""
echo "=== DONE! Native libraries built for $ABI ==="