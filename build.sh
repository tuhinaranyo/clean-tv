#!/bin/bash
# Builds launcher/build/launcher.apk with plain SDK tools (no Gradle). Needs JDK 17 + Android SDK (platforms;android-34, build-tools;34.0.0).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HERE"/toolchain/jdk-17*/Contents/Home 2>/dev/null | head -1)}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_SDK_ROOT:-$HERE/toolchain/sdk}"
BT="$SDK/build-tools/34.0.0"; JAR="$SDK/platforms/android-34/android.jar"
SRC="$HERE/launcher/app/src/main"; OUT="$HERE/launcher/build"
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/res"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest "$SRC/AndroidManifest.xml" -A "$SRC/assets" \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 33 "$OUT"/res/*.flat
find "$SRC/java" "$OUT/gen" -name '*.java' | sed 's/.*/"&"/' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$JAR" -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v "warning\|^Note" || true
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --min-api 26 --lib "$JAR" --output "$OUT" "$OUT/classes.jar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"; (cd "$OUT" && zip -q -j unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS="$HERE/toolchain/debug.keystore"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Debug" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$OUT/launcher.apk" "$OUT/aligned.apk"
ls -la "$OUT/launcher.apk"
