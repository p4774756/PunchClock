#!/usr/bin/env sh
# 開發筆電狀態回報（POSIX sh、只依賴 curl）。
# 設定檔：~/.punchclock/dev-status.env（權限建議 600，非 600 會警告到 stderr，不含 token）。
#
# launchd 安裝範例：複製 scripts/dev-heartbeat.example.plist 到
#   ~/Library/LaunchAgents/com.punchclock.dev-heartbeat.plist
# 將其中的腳本路徑改為本 repo 的絕對路徑，再執行：
#   launchctl load ~/Library/LaunchAgents/com.punchclock.dev-heartbeat.plist
#
# 子指令：start <issue> <branch> | stop | beat | loop

set +e

ENV_FILE="${DEV_STATUS_ENV:-$HOME/.punchclock/dev-status.env}"
RUN_FILE="$HOME/.punchclock/dev-status-run"
DEFAULT_URL="https://punchclock.onrender.com/api/dev-machines/heartbeat"

warn_perms() {
  if [ ! -f "$ENV_FILE" ]; then
    return
  fi
  mode=$(stat -f '%OLp' "$ENV_FILE" 2>/dev/null || stat -c '%a' "$ENV_FILE" 2>/dev/null)
  if [ -n "$mode" ] && [ "$mode" != "600" ]; then
    echo "警告：$ENV_FILE 權限應為 600（目前 $mode）" >&2
  fi
}

load_env() {
  if [ ! -f "$ENV_FILE" ]; then
    return 1
  fi
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  return 0
}

pick_env() {
  key=$1
  eval "v=\${$key:-}"
  printf '%s' "$v"
}

agent_running() {
  if pgrep -f 'cursor-agent' >/dev/null 2>&1; then
    return 0
  fi
  if [ -x "$HOME/.local/bin/agent" ] && pgrep -f "$HOME/.local/bin/agent" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

read_battery() {
  if ! command -v pmset >/dev/null 2>&1; then
    return
  fi
  batt=$(pmset -g batt 2>/dev/null | head -n 2 | tail -n 1)
  pct=$(printf '%s' "$batt" | sed -n 's/.*\([0-9][0-9]*\)%;.*/\1/p')
  if [ -z "$pct" ]; then
    return
  fi
  charging=false
  case "$batt" in
    *charging*|*AC\ Power*) charging=true ;;
  esac
  BATTERY_JSON=",\"battery\":$pct,\"charging\":$charging"
}

read_run() {
  ISSUE=""
  BRANCH=""
  STARTED=""
  if [ -f "$RUN_FILE" ]; then
    ISSUE=$(sed -n '1p' "$RUN_FILE")
    BRANCH=$(sed -n '2p' "$RUN_FILE")
    STARTED=$(sed -n '3p' "$RUN_FILE")
  fi
}

git_commit() {
  repo=$(pick_env PUNCHCLOCK_REPO)
  if [ -z "$repo" ]; then
    repo="$HOME/PunchClock"
  fi
  if command -v git >/dev/null 2>&1 && [ -d "$repo/.git" ]; then
    sha=$(git -C "$repo" rev-parse HEAD 2>/dev/null)
    if [ -n "$sha" ]; then
      COMMIT_JSON=",\"commit\":\"$sha\""
    fi
  fi
}

do_beat() {
  if ! load_env; then
    exit 0
  fi
  warn_perms
  url=$(pick_env DEV_STATUS_URL)
  if [ -z "$url" ]; then
    url="$DEFAULT_URL"
  fi
  token=$(pick_env DEV_STATUS_TOKEN)
  machine=$(pick_env DEV_STATUS_MACHINE)
  if [ -z "$token" ] || [ -z "$machine" ]; then
    exit 0
  fi
  read_run
  BATTERY_JSON=""
  COMMIT_JSON=""
  read_battery
  git_commit
  if agent_running; then
    agent=true
  else
    agent=false
  fi
  issue_json="null"
  if [ -n "$ISSUE" ]; then
    issue_json="$ISSUE"
  fi
  branch_json="null"
  if [ -n "$BRANCH" ]; then
    branch_json="\"$BRANCH\""
  fi
  started_json="null"
  if [ -n "$STARTED" ]; then
    started_json="$STARTED"
  fi
  payload=$(printf '{"machine":"%s","issue":%s,"branch":%s,"runStartedAt":%s,"agentRunning":%s%s%s}' \
    "$machine" "$issue_json" "$branch_json" "$started_json" "$agent" "$COMMIT_JSON" "$BATTERY_JSON")
  if ! command -v curl >/dev/null 2>&1; then
    exit 0
  fi
  curl -sS -o /dev/null \
    --max-time 10 \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "$payload" \
    "$url" >/dev/null 2>&1
  exit 0
}

cmd_start() {
  issue=$1
  branch=$2
  if [ -z "$issue" ] || [ -z "$branch" ]; then
    exit 0
  fi
  mkdir -p "$(dirname "$RUN_FILE")"
  started=$(date +%s)000
  printf '%s\n%s\n%s\n' "$issue" "$branch" "$started" > "$RUN_FILE"
  do_beat
}

cmd_stop() {
  rm -f "$RUN_FILE" 2>/dev/null
  ISSUE=""
  BRANCH=""
  STARTED=""
  do_beat
}

cmd_loop() {
  while true; do
    do_beat
    sleep 60
  done
}

case "$1" in
  start) cmd_start "$2" "$3" ;;
  stop) cmd_stop ;;
  beat) do_beat ;;
  loop) cmd_loop ;;
  *) exit 0 ;;
esac