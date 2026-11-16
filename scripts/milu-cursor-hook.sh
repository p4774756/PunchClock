#!/usr/bin/env sh
# PunchClock milu ← Cursor hooks（只觀察、不阻擋）
# 從 stdin 讀 hook JSON，POST 到本機 127.0.0.1 的桌寵監聽。
# 連不到／逾時／沒開寵物：丟掉錯誤、印安全 JSON、結束碼 0。
#
# 工作目錄：
#   - 專案級 .cursor/hooks.json：repo 根目錄 → command 用 ./scripts/milu-cursor-hook.sh
#   - 使用者級 ~/.cursor/hooks.json：~/.cursor/ → 請改成腳本的絕對路徑
# 建議把 scripts/milu-cursor-hooks.example.json 複製到 ~/.cursor/hooks.json（只影響本機 IDE）。

set +e

HOOK_FILE="${MILU_HOOK_FILE:-$HOME/.punchclock/milu-hook.json}"
SAFE_JSON='{"continue":true}'

emit_ok() {
  printf '%s\n' "$SAFE_JSON"
  exit 0
}

# 讀完整 stdin（可能很大）；我們只轉傳必要欄位，不把內容寫到磁碟。
BODY=$(cat 2>/dev/null)
if [ -z "$BODY" ]; then
  emit_ok
fi

if [ ! -f "$HOOK_FILE" ]; then
  emit_ok
fi

# 極簡解析：不依賴 jq。
pick() {
  # $1 = key
  printf '%s' "$BODY" | tr '\n' ' ' | sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" | head -n 1
}

EVENT=$(pick hook_event_name)
CONV=$(pick conversation_id)
if [ -z "$CONV" ]; then
  CONV=$(pick session_id)
fi
STATUS=$(pick status)

PORT=$(sed -n 's/.*"port"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$HOOK_FILE" | head -n 1)
TOKEN=$(sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$HOOK_FILE" | head -n 1)
URL=$(sed -n 's/.*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$HOOK_FILE" | head -n 1)

if [ -z "$PORT" ] && [ -z "$URL" ]; then
  emit_ok
fi

if [ -z "$URL" ]; then
  URL="http://127.0.0.1:${PORT}/hook"
fi

# 只送觀察用欄位，避免把 prompt／路徑轉進本機。
PAYLOAD=$(printf '{"hook_event_name":"%s","conversation_id":"%s","status":"%s"}' \
  "$EVENT" "$CONV" "$STATUS")

if command -v curl >/dev/null 2>&1; then
  if [ -n "$TOKEN" ]; then
    curl -sS -o /dev/null \
      --connect-timeout 0.1 --max-time 0.3 \
      -H "Content-Type: application/json" \
      -H "X-Milu-Token: $TOKEN" \
      -d "$PAYLOAD" \
      "$URL" >/dev/null 2>&1
  else
    curl -sS -o /dev/null \
      --connect-timeout 0.1 --max-time 0.3 \
      -H "Content-Type: application/json" \
      -d "$PAYLOAD" \
      "$URL" >/dev/null 2>&1
  fi
fi

emit_ok
