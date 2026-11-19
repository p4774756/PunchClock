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

worktree_head_short() {
  wt=$1
  if [ -z "$wt" ]; then
    return 1
  fi
  if [ -n "$MOCK_GIT_SHORT" ]; then
    printf '%s' "$MOCK_GIT_SHORT"
    return 0
  fi
  if command -v git >/dev/null 2>&1 && { [ -d "$wt/.git" ] || [ -d "$wt" ]; }; then
    sha=$(git -C "$wt" rev-parse --short HEAD 2>/dev/null)
    if [ -n "$sha" ]; then
      printf '%s' "$sha"
      return 0
    fi
  fi
  return 1
}

pick_longest_run_from_json() {
  printf '%s' "$1" | awk '
    BEGIN { best_st = -1; bi = ""; bb = ""; bst = -1 }
    {
      s = $0
      gsub(/^\[/, "", s)
      gsub(/\]$/, "", s)
      n = split(s, chunks, /},{/)
      for (i = 1; i <= n; i++) {
        c = chunks[i]
        if (i < n) c = c "}"
        issue = 0; branch = ""; st = -1
        if (match(c, /"issue":([0-9]+)/)) issue = substr(c, RSTART + 8, RLENGTH - 8) + 0
        if (match(c, /"branch":"[^"]+"/)) branch = substr(c, RSTART + 10, RLENGTH - 11)
        if (match(c, /"startedAt":([0-9]+)/)) st = substr(c, RSTART + 13, RLENGTH - 13) + 0
        if (st >= 0 && (best_st < 0 || st < best_st)) {
          best_st = st
          bi = issue
          bb = branch
          bst = st
        }
      }
    }
    END {
      if (best_st >= 0) printf "%s|%s|%s", bi, bb, bst
    }
  '
}

# 解析 ps 輸出中的 run-wt.sh run<N>[-f<K>] … <worktree>
read_wt_runs() {
  RUNS_JSON="[]"
  if [ -n "$MOCK_PS_OUTPUT" ]; then
    ps_out="$MOCK_PS_OUTPUT"
  else
    ps_out=$(ps -axo etime=,command= 2>/dev/null)
  fi
  [ -z "$ps_out" ] && return 0
  now_ms=$(date +%s)000
  tsv=$(printf '%s\n' "$ps_out" | awk -v now_ms="$now_ms" '
    function etime_ms(e,    n, d, h, m, s) {
      gsub(/^[ \t]+|[ \t]+$/, "", e)
      n = split(e, p, /[-:]/)
      if (e ~ /^[0-9]+-[0-9]+:[0-9]+:[0-9]+$/) {
        d = p[1] + 0; h = p[2] + 0; m = p[3] + 0; s = p[4] + 0
        return (d * 86400000 + h * 3600000 + m * 60000 + s * 1000)
      }
      if (e ~ /^[0-9]+:[0-9]+:[0-9]+$/) {
        h = p[1] + 0; m = p[2] + 0; s = p[3] + 0
        return (h * 3600000 + m * 60000 + s * 1000)
      }
      if (e ~ /^[0-9]+:[0-9]+$/) {
        m = p[1] + 0; s = p[2] + 0
        return (m * 60000 + s * 1000)
      }
      return 0
    }
    function json_str(s) {
      gsub(/\\/, "\\\\", s)
      gsub(/"/, "\\\"", s)
      return s
    }
  function is_run_wt_argv(    sh, script, tok) {
    sh = $2
    if (sh ~ /(^|\/)(sh|bash)$/) {
      script = $3
      tok = $4
      if (script ~ /(^|\/)run-wt\.sh$/ && tok ~ /^run[0-9]+(-f[0-9]+)?$/) {
        run_argv_tok = tok
        return 1
      }
    }
    script = $2
    tok = $3
    if (script ~ /(^|\/)run-wt\.sh$/ && tok ~ /^run[0-9]+(-f[0-9]+)?$/) {
      run_argv_tok = tok
      return 1
    }
    return 0
  }
    BEGIN { count = 0 }
    {
      if (!is_run_wt_argv()) next
      et = $1
      elapsed = etime_ms(et)
      started = now_ms - elapsed
      issue = 0
      round = 0
      tok = run_argv_tok
      sub(/^run/, "", tok)
      if (match(tok, /^[0-9]+/)) {
        issue = substr(tok, RSTART, RLENGTH) + 0
      }
      if (match(tok, /-f[0-9]+/)) {
        rpart = substr(tok, RSTART, RLENGTH)
        sub(/^-f/, "", rpart)
        round = rpart + 0
      }
      if (issue < 1) next
      branch = "ai/issue-" issue
      wt = ""
      n = NF
      while (n > 1) {
        cand = $n
        if (cand ~ /^(\/|~)/) {
          wt = cand
          break
        }
        n--
      }
      if (!(issue in best_started) || started > best_started[issue]) {
        best_started[issue] = started
        best_round[issue] = round
        best_branch[issue] = branch
        best_wt[issue] = wt
      }
    }
    END {
      for (i in best_started) {
        if (count >= 12) continue
        printf "%s\t%s\t%s\t%s\t%s\n", i, best_round[i], best_branch[i], best_started[i], best_wt[i]
        count++
      }
    }
  ')
  if [ -z "$tsv" ]; then
    RUNS_JSON="[]"
    return 0
  fi
  json="["
  sep=""
  while IFS='	' read -r issue round branch started wt; do
    [ -z "$issue" ] && continue
    commit_json=""
    if [ -n "$wt" ]; then
      sha=$(worktree_head_short "$wt") || sha=""
      if [ -n "$sha" ]; then
        commit_json=$(printf ',"commit":"%s"' "$sha")
      fi
    fi
    wt_json=""
    if [ -n "$wt" ]; then
      wt_json=$(printf ',"worktree":"%s"' "$wt")
    fi
    part=$(printf '{"issue":%s,"round":%s,"branch":"%s","startedAt":%s%s%s}' \
      "$issue" "$round" "$branch" "$started" "$wt_json" "$commit_json")
    json="$json$sep$part"
    sep=","
  done <<EOF
$tsv
EOF
  json="$json]"
  RUNS_JSON="$json"
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
  read_wt_runs
  if [ -z "$ISSUE" ] && [ "$RUNS_JSON" != "[]" ]; then
  picked=$(pick_longest_run_from_json "$RUNS_JSON")
    if [ -n "$picked" ]; then
      ISSUE=$(printf '%s' "$picked" | cut -d'|' -f1)
      BRANCH=$(printf '%s' "$picked" | cut -d'|' -f2)
      STARTED=$(printf '%s' "$picked" | cut -d'|' -f3)
    fi
  fi
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
  runs_json="$RUNS_JSON"
  payload=$(printf '{"machine":"%s","issue":%s,"branch":%s,"runStartedAt":%s,"agentRunning":%s,"runs":%s%s%s}' \
    "$machine" "$issue_json" "$branch_json" "$started_json" "$agent" "$runs_json" "$COMMIT_JSON" "$BATTERY_JSON")
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