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
# jpackage 只接受數字版號。tag v4.0.0-r2 這類重跑標記去掉後綴。
VERSION="${VERSION%%-*}"

trim_playwright_driver() {
  local target="$1"
  local pkg="$2"
  # Windows runner 的 Git Bash 不一定有 zip。用 Python 重寫 jar：
  # 只留目標平台的 driver JavaScript，拿掉 node 執行檔（單顆約 30–40MB）。
  local py="python3"
  if ! command -v python3 >/dev/null 2>&1; then
    py="python"
  fi
  "$py" - "$target" "$pkg" "$(uname -m)" << 'PY'
import os
import sys
import zipfile

target, pkg, arch = sys.argv[1], sys.argv[2], sys.argv[3]
if pkg == "exe":
    keep = "win32_x64"
elif arch == "arm64":
    keep = "mac-arm64"
else:
    keep = "mac"
node_names = {"node", "node.exe"}
tmp = target + ".trim"
with zipfile.ZipFile(target, "r") as src, zipfile.ZipFile(tmp, "w") as dst:
    for info in src.infolist():
        parts = info.filename.split("/")
        if len(parts) >= 2 and parts[0] == "driver":
            if parts[1] != keep or parts[-1] in node_names:
                continue
        out = zipfile.ZipInfo(filename=info.filename, date_time=info.date_time)
        out.compress_type = info.compress_type
        out.external_attr = info.external_attr
        dst.writestr(out, src.read(info.filename))
os.replace(tmp, target)
PY
}

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — run mvn package first" >&2
  exit 1
fi

mkdir -p "$DESKTOP_BUILD"
rm -rf "$RUNTIME_IMAGE"

PACK_JAR="$DESKTOP_BUILD/SKLbaby-pack.jar"
cp "$JAR" "$PACK_JAR"
trim_playwright_driver "$PACK_JAR" "$PACKAGE_TYPE"

if jar tf "$PACK_JAR" | grep -E 'driver/.*/node(\.exe)?$' >/dev/null; then
  echo "Playwright node binary is still inside the pack jar" >&2
  exit 1
fi
if ! jar tf "$PACK_JAR" | grep -E 'driver/.*/package/package.json$' >/dev/null; then
  echo "Playwright driver package is missing from the pack jar" >&2
  exit 1
fi

MODULES="$(jdeps --print-module-deps --ignore-missing-deps --multi-release 11 -q "$PACK_JAR")"
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
  --compress=zip-9 \
  --output "$RUNTIME_IMAGE"

INPUT_DIR="$DESKTOP_BUILD/input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$PACK_JAR" "$INPUT_DIR/SKLbaby.jar"

JPACKAGE_ARGS=(
  --name "$APP_NAME"
  --app-version "$VERSION"
  --vendor "PunchClock"
  --input "$INPUT_DIR"
  --main-jar SKLbaby.jar
  --main-class "$MAIN_CLASS"
  --runtime-image "$RUNTIME_IMAGE"
  --icon "$ICON"
  --java-options "-Dfile.encoding=UTF-8"
  --java-options "-Djava.awt.headless=false"
)

OUT_FILE=""
if [[ "$PACKAGE_TYPE" == "dmg" ]]; then
  STAGE="$DESKTOP_BUILD/dmg-root"
  rm -rf "$STAGE"
  mkdir -p "$STAGE"
  jpackage "${JPACKAGE_ARGS[@]}" --type app-image --dest "$STAGE"
  OUT_FILE="$DESKTOP_BUILD/SKLbaby-mac.dmg"
  rm -f "$OUT_FILE"
  # UDZO 對已壓縮的 jar 幫助不大；UDBZ 能再壓 runtime 裡的 libjvm。
  hdiutil create -quiet -volname "$APP_NAME" -srcfolder "$STAGE" -ov -format UDBZ "$OUT_FILE"
elif [[ "$PACKAGE_TYPE" == "exe" ]]; then
  jpackage "${JPACKAGE_ARGS[@]}" --type exe --win-dir-chooser --win-menu --win-shortcut --dest "$DESKTOP_BUILD"
  produced="$DESKTOP_BUILD/${APP_NAME}-${VERSION}.exe"
  if [[ ! -f "$produced" ]]; then
    echo "jpackage did not produce $produced" >&2
    exit 1
  fi
  OUT_FILE="$DESKTOP_BUILD/SKLbaby-win.exe"
  mv -f "$produced" "$OUT_FILE"
else
  echo "Unsupported package type: $PACKAGE_TYPE" >&2
  exit 1
fi

if [[ ! -f "$OUT_FILE" ]]; then
  echo "jpackage did not produce an installer" >&2
  exit 1
fi

bytes="$(wc -c < "$OUT_FILE" | tr -d ' ')"
echo "Built $OUT_FILE ($bytes bytes)"
max_bytes=$((60 * 1024 * 1024))
min_bytes=$((15 * 1024 * 1024))
if (( bytes > max_bytes || bytes < min_bytes )); then
  echo "Installer size $bytes is outside 15–60 MiB" >&2
  exit 1
fi
