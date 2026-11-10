#!/bin/bash
# 博客写作 APP 构建脚本（aapt2 + javac + d8 + apksigner，无 Gradle）
set -e
ROOT="C:/Users/Administrator/Desktop/my-blog/mobile-app"
BT="C:/Users/Administrator/android-sdk/android-14"
PLAT="C:/Users/Administrator/android-sdk/android-34/android.jar"
APP="$ROOT"
B="$ROOT/build"
JAVA=/d/jdk-11/bin

mkdir -p "$B/gen" "$B/classes" "$B/dex"

"$BT/aapt2.exe" compile --dir "$APP/res" -o "$B/res.zip"
"$BT/aapt2.exe" link -o "$B/base.apk" -I "$PLAT" --manifest "$APP/AndroidManifest.xml" -R "$B/res.zip" --java "$B/gen" --auto-add-overlay

find "$APP/java" "$B/gen" -name "*.java" > "$B/sources.txt"
"$JAVA/javac" -encoding UTF-8 -source 8 -target 8 -classpath "$PLAT" -d "$B/classes" @"$B/sources.txt" 2>&1 | grep -v bootstrap || true
"$JAVA/jar" cf "$B/classes.jar" -C "$B/classes" .
"$JAVA/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --lib "$PLAT" --output "$B/dex" "$B/classes.jar"

python "$ROOT/merge_dex.py" "$B/base.apk" "$B/dex/classes.dex" "$B/unsigned.apk"
"$BT/zipalign.exe" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"

"$JAVA/java" -jar "$BT/lib/apksigner.jar" sign \
  --ks "$ROOT/release.jks" --ks-pass pass:blogwriter123 --key-pass pass:blogwriter123 \
  --out "$ROOT/博客写作-v1.0.apk" "$B/aligned.apk"

"$JAVA/java" -jar "$BT/lib/apksigner.jar" verify "$ROOT/博客写作-v1.0.apk" && echo "== BUILD OK =="
