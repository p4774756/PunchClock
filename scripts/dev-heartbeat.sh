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
DEFAULT_URL="https://ping-pong-server-jbot.onrender.com/api/dev-machines/heartbeat"

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

# 供 self-test 使用：從 pmset 電量行擷取百分比
parse_battery_pct() {
  batt=$1
  pct=$(printf '%s' "$batt" | sed -n 's/.*\t\([0-9][0-9]*\)%;.*/\1/p')
  if [ -z "$pct" ]; then
    pct=$(printf '%s' "$batt" | sed -n 's/.*[^0-9]\([0-9][0-9]*\)%;.*/\1/p')
  fi
  if [ -z "$pct" ]; then
    pct=$(printf '%s' "$batt" | sed -n 's/^\([0-9][0-9]*\)%;.*/\1/p')
  fi
  printf '%s' "$pct"
}

# 供 self-test 使用：charging=true/false
parse_battery_charging() {
  batt_line=$1
  first_line=$2
  case "$first_line" in
    *'AC Power'*)
      printf 'true'
      return
    ;;
  esac
  state=$(printf '%s' "$batt_line" | sed -n 's/.*%;[[:space:]]*\([^;]*\).*/\1/p' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
  case "$state" in
    discharging) printf 'false' ;;
    charging|charged) printf 'true' ;;
    *AC\ attached*) printf 'true' ;;
    *) printf 'false' ;;
  esac
}

json_issue_field() {
  issue=$1
  case "$issue" in
    ''|*[!0-9]*) printf 'null' ;;
    *) printf '%s' "$issue" ;;
  esac
}

json_branch_field() {
  branch=$1
  if [ -z "$branch" ]; then
    printf 'null'
    return
  fi
  if printf '%s' "$branch" | LC_ALL=C grep -Eq '^[A-Za-z0-9._/-]{1,100}$'; then
    printf '"%s"' "$branch"
  else
    printf 'null'
  fi
}

json_started_field() {
  started=$1
  case "$started" in
    ''|*[!0-9]*) printf 'null' ;;
    *) printf '%s' "$started" ;;
  esac
}

read_battery() {
  if ! command -v pmset >/dev/null 2>&1; then
    return
  fi
  pmset_out=$(pmset -g batt 2>/dev/null)
  first_line=$(printf '%s\n' "$pmset_out" | sed -n '1p')
  batt=$(printf '%s\n' "$pmset_out" | sed -n '2p')
  pct=$(parse_battery_pct "$batt")
  if [ -z "$pct" ]; then
    return
  fi
  charging=$(parse_battery_charging "$batt" "$first_line")
  BATTERY_JSON=",\"battery\":$pct,\"charging\":$charging"
}

# 第 3 行可為 epoch 毫秒或 ISO 8601（dev-run-mark.sh 寫入後者）
parse_run_started_ms() {
  raw=$1
  if [ -z "$raw" ]; then
    return
  fi
  case "$raw" in
    *[!0-9]*)
      sec=""
      sec=$(date -j -u -f "%Y-%m-%dT%H:%M:%SZ" "$raw" +%s 2>/dev/null) || true
      if [ -z "$sec" ]; then
        sec=$(date -u -d "$raw" +%s 2>/dev/null) || true
      fi
      if [ -n "$sec" ]; then
        printf '%s000' "$sec"
      fi
      ;;
    *) printf '%s' "$raw" ;;
  esac
}

read_run() {
  ISSUE=""
  BRANCH=""
  STARTED=""
  if [ -f "$RUN_FILE" ]; then
    ISSUE=$(sed -n '1p' "$RUN_FILE")
    BRANCH=$(sed -n '2p' "$RUN_FILE")
    STARTED=$(parse_run_started_ms "$(sed -n '3p' "$RUN_FILE")")
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
  issue_json=$(json_issue_field "$ISSUE")
  branch_json=$(json_branch_field "$BRANCH")
  started_json=$(json_started_field "$STARTED")
  payload=$(printf '{"machine":"%s","issue":%s,"branch":%s,"runStartedAt":%s,"agentRunning":%s%s%s}' \
    "$machine" "$issue_json" "$branch_json" "$started_json" "$agent" "$COMMIT_JSON" "$BATTERY_JSON")
  if ! command -v curl >/dev/null 2>&1; then
    exit 0
  fi
  printf 'header = "Authorization: Bearer %s"\n' "$token" | curl -sS -K - \
    -o /dev/null \
    --max-time 10 \
    -H "Content-Type: application/json" \
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

if [ "${DEV_HEARTBEAT_LIB:-}" != "1" ]; then
  case "$1" in
    start) cmd_start "$2" "$3" ;;
    stop) cmd_stop ;;
    beat) do_beat ;;
    loop) cmd_loop ;;
    *) exit 0 ;;
  esac
fi