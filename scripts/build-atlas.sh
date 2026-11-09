#!/usr/bin/env bash
# 建置中醫人體靜態站，供本機 server 以 /atlas/tcm-3d/ 提供。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/atlas-src/tcm-3d"
BASE="${ATLAS_BASE:-/atlas/tcm-3d/}"

if ! command -v node >/dev/null || ! command -v npm >/dev/null; then
  echo "需要 Node.js 與 npm（建議 ≥ 22）。" >&2
  exit 1
fi

if [[ ! -d "$APP" ]]; then
  echo "找不到 $APP" >&2
  exit 1
fi

cd "$APP"
if [[ ! -d node_modules ]]; then
  echo "首次建置：安裝依賴…"
  npm ci
fi

echo "建置中醫人體 (base=${BASE}) ..."
if [[ "${BASE}" == "/atlas/tcm-3d/" ]]; then
  npm run build:punchclock
else
  npm run build -- --base "${BASE}"
fi

if [[ ! -f "${APP}/dist/index.html" ]]; then
  echo "建置失敗：沒有 dist/index.html" >&2
  exit 1
fi

echo
echo "完成：${APP}/dist"
echo "啟動 server 後開啟：http://localhost:3000${BASE}?set=head-neck-21"
echo "(server 需在 repo 根目錄或能找到 atlas-src/tcm-3d/dist)"
