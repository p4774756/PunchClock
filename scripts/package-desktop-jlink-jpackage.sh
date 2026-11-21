#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/client/target/SKLbaby.jar"
RUNTIME_IMAGE="$ROOT/build/desktop-runtime"
DESKTOP_BUILD="$ROOT/build/desktop"
APP_NAME="新光小寶貝"
MAIN_CLASS="com.example.SklbabyLauncher"
ICON="${1:?icon file (.icns or .ico)}"
PACKAGE_TYPE="${2:?dmg|exe}"
VERSION="${3:-4.0.0}"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — run mvn package first" >&2
  exit 1
fi

mkdir -p "$DESKTOP_BUILD"
rm -rf "$RUNTIME_IMAGE"

MODULES="$(jdeps --print-module-deps --ignore-missing-deps --multi-release 11 -q "$JAR")"
# jlink 最少要含 desktop／http；jdeps 若漏掉則補上。
for extra in java.desktop java.net.http java.sql java.naming jdk.crypto.ec; do
  if [[ ",$MODULES," != *",$extra,"* ]]; then
    MODULES="${MODULES:+$MODULES,}$extra"
  fi
done

jlink \
  --add-modules "$MODULES" \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2 \
  --output "$RUNTIME_IMAGE"

INPUT_DIR="$DESKTOP_BUILD/input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$JAR" "$INPUT_DIR/SKLbaby.jar"

JPACKAGE_ARGS=(
  --name "$APP_NAME"
  --app-version "$VERSION"
  --vendor "PunchClock"
  --input "$INPUT_DIR"
  --main-jar SKLbaby.jar
  --main-class "$MAIN_CLASS"
  --runtime-image "$RUNTIME_IMAGE"
  --dest "$DESKTOP_BUILD"
  --icon "$ICON"
  --java-options "-Dfile.encoding=UTF-8"
  --java-options "-Djava.awt.headless=false"
)

if [[ "$PACKAGE_TYPE" == "dmg" ]]; then
  JPACKAGE_ARGS+=(--type dmg)
  OUT_NAME="SKLbaby-mac.dmg"
elif [[ "$PACKAGE_TYPE" == "exe" ]]; then
  JPACKAGE_ARGS+=(--type exe --win-dir-chooser --win-menu --win-shortcut)
  OUT_NAME="SKLbaby-win.exe"
else
  echo "Unsupported package type: $PACKAGE_TYPE" >&2
  exit 1
fi

jpackage "${JPACKAGE_ARGS[@]}"

FOUND="$(find "$DESKTOP_BUILD" -maxdepth 1 -type f \( -name '*.dmg' -o -name '*.exe' \) | head -n 1)"
if [[ -z "$FOUND" ]]; then
  echo "jpackage did not produce an installer" >&2
  exit 1
fi
mv -f "$FOUND" "$DESKTOP_BUILD/$OUT_NAME"
echo "Built $DESKTOP_BUILD/$OUT_NAME"