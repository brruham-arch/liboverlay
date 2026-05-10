#!/data/data/com.termux/files/usr/bin/bash
# build_dex.sh - Compile OverlayBridge.java → overlay_bridge.dex

set -e

D8="$HOME/android-sdk/build-tools/34.0.0/d8"
ANDROID_JAR="$HOME/android-sdk/platforms/android-33/android.jar"
SRC="bridge/com/brruham/overlay/OverlayBridge.java"
OUT_DIR="build_dex"
DEX_OUT="overlay_bridge.dex"

if [ ! -f "$D8" ]; then
    echo "[ERROR] d8 tidak ditemukan: $D8"
    exit 1
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "[ERROR] android.jar tidak ditemukan: $ANDROID_JAR"
    echo "Jalankan: sdkmanager 'platforms;android-33'"
    exit 1
fi

echo "[1/3] Compile Java → .class"
mkdir -p "$OUT_DIR/classes"
javac \
    -bootclasspath "$ANDROID_JAR" \
    -cp "$ANDROID_JAR" \
    -d "$OUT_DIR/classes" \
    "$SRC"

echo "[2/3] .class → DEX"
mkdir -p "$OUT_DIR/dex"
"$D8" \
    --release \
    --min-api 21 \
    --lib "$ANDROID_JAR" \
    --output "$OUT_DIR/dex" \
    $(find "$OUT_DIR/classes" -name "*.class")

echo "[3/3] Output"
cp "$OUT_DIR/dex/classes.dex" "$DEX_OUT"

echo ""
echo "====================================="
echo " SUKSES: $DEX_OUT"
echo "====================================="
echo ""
echo "Copy ke game:"
echo "  cp $DEX_OUT /sdcard/Android/data/com.sampmobilerp.game/files/overlay_bridge.dex"
