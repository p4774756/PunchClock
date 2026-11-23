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

# 開發鏈腳本仍在跑也算工作中（不等於 cursor-agent）。
dev_chains_running() {
  if [ -n "$MOCK_DEV_CHAINS" ]; then
  case "$MOCK_DEV_CHAINS" in
    1|true|yes) return 0 ;;
  esac
    return 1
  fi
  ps_out=$(ps -axo command= 2>/dev/null) || return 1
  case "$ps_out" in
    *chainrun.sh*|*chainmed.sh*|*chainfast2.sh*|*loopfast.sh*) return 0 ;;
  esac
  return 1
}

LAG_FILE="${DEV_HEARTBEAT_LAG_FILE:-$HOME/.punchclock/dev-heartbeat-lag.json}"
LAG_MS=300000

read_lag_file() {
  LAG_RUNS_JSON=""
  LAG_AGENT=""
  LAG_UNTIL=0
  if [ ! -f "$LAG_FILE" ]; then
    return 0
  fi
  LAG_RUNS_JSON=$(sed -n 's/.*"runs":\(\[[^]]*\]\).*/\1/p' "$LAG_FILE" | head -n 1)
  LAG_AGENT=$(sed -n 's/.*"agent":\(true\|false\).*/\1/p' "$LAG_FILE" | head -n 1)
  LAG_UNTIL=$(sed -n 's/.*"until":\([0-9][0-9]*\).*/\1/p' "$LAG_FILE" | head -n 1)
}

write_lag_file() {
  runs_json=$1
  agent=$2
  until_ms=$3
  if [ -z "$runs_json" ]; then
    runs_json="[]"
  fi
  mkdir -p "$(dirname "$LAG_FILE")" 2>/dev/null || true
  printf '{"runs":%s,"agent":%s,"until":%s}\n' "$runs_json" "$agent" "$until_ms" >"$LAG_FILE" 2>/dev/null || true
}

apply_runs_lag() {
  now_ms=$(date +%s)000
  read_lag_file
  if [ -n "$RUNS_JSON" ] && [ "$RUNS_JSON" != "[]" ]; then
    write_lag_file "$RUNS_JSON" "$agent" "$((now_ms + LAG_MS))"
    return 0
  fi
  if [ -n "$LAG_UNTIL" ] && [ "$LAG_UNTIL" -gt "$now_ms" ] && [ -n "$LAG_RUNS_JSON" ] && [ "$LAG_RUNS_JSON" != "[]" ]; then
    RUNS_JSON="$LAG_RUNS_JSON"
  fi
}

apply_agent_lag() {
  now_ms=$(date +%s)000
  if [ "$agent" = "true" ]; then
    return 0
  fi
  read_lag_file
  if [ "$LAG_AGENT" = "true" ] && [ -n "$LAG_UNTIL" ] && [ "$LAG_UNTIL" -gt "$now_ms" ]; then
    agent=true
  fi
  if dev_chains_running; then
    agent=true
  fi
}

read_waiter_queued() {
  QUEUED_JSON="[]"
  if [ -n "$MOCK_PS_WAITERS" ]; then
    ps_out="$MOCK_PS_WAITERS"
  else
    ps_out=$(ps -axo command= 2>/dev/null)
  fi
  [ -z "$ps_out" ] && return 0
  tsv=$(printf '%s\n' "$ps_out" | awk '
    BEGIN { count = 0 }
    {
      line = $0
      if (line !~ /waiter\.sh/) next
      issue = 0
      after = 0
      if (match(line, /i[0-9]+-waiter/)) {
        tok = substr(line, RSTART, RLENGTH)
        sub(/^i/, "", tok)
        sub(/-waiter.*/, "", tok)
        issue = tok + 0
      }
      if (match(line, /after[0-9]+/)) {
        tok = substr(line, RSTART, RLENGTH)
        sub(/^after/, "", tok)
        after = tok + 0
      }
      if (issue < 1) next
      key = issue SUBSEP after
      if (!(key in seen)) {
        seen[key] = 1
        if (count >= 12) next
        count++
        printf "%s\t%s\n", issue, after
      }
    }
  ')
  [ -z "$tsv" ] && return 0
  json="["
  sep=""
  while IFS='	' read -r issue after; do
    [ -z "$issue" ] && continue
    if [ -n "$after" ] && [ "$after" -gt 0 ] 2>/dev/null; then
      part=$(printf '{"issue":%s,"afterIssue":%s}' "$issue" "$after")
    else
      part=$(printf '{"issue":%s}' "$issue")
    fi
    json="$json$sep$part"
    sep=","
  done <<EOF
$tsv
EOF
  json="$json]"
  QUEUED_JSON="$json"
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

json_string_field() {
  s=$1
  if [ -z "$s" ]; then
    printf 'null'
    return
  fi
  if printf '%s' "$s" | LC_ALL=C grep -Eq '^[A-Za-z0-9._+-]{1,64}$'; then
    printf '"%s"' "$s"
  else
    printf 'null'
  fi
}

# 顯示名稱／位置：UTF-8 字元數 1..24（不是 byte）。拒絕控制字元、換行與不合法 UTF-8。
# 空值為 null。寫進 JSON 時跳脫雙引號與反斜線。
# od／awk 的 LC_ALL=C 只為了逐 byte 解 UTF-8，不用 [[:print:]] 判斷字元。
json_display_field() {
  s=$1
  if [ -z "$s" ]; then
    printf 'null'
    return
  fi
  printf '%s' "$s" | LC_ALL=C od -An -v -tu1 | LC_ALL=C awk '
    function fail() { bad = 1; exit }
    BEGIN { n = 0; out = ""; bad = 0; need = 0; lead = 0; seen2 = 0; bytes = 0; acc = "" }
    {
      for (i = 1; i <= NF; i++) {
        b = $i + 0
        bytes++
        if (bytes > 96) fail()
        if (need > 0) {
          if (b < 128 || b > 191) fail()
          if (seen2 == 0) {
            seen2 = 1
            if (lead == 224 && b < 160) fail()
            if (lead == 237 && b > 159) fail()
            if (lead == 240 && b < 144) fail()
            if (lead == 244 && b > 143) fail()
            if (lead == 194 && b < 160) fail()
          }
          acc = acc sprintf("%c", b)
          need--
          if (need == 0) {
            n++
            if (n > 24) fail()
            out = out acc
            acc = ""
            lead = 0
            seen2 = 0
          }
          continue
        }
        if (b < 32 || b == 127) fail()
        if (b < 128) {
          n++
          if (n > 24) fail()
          if (b == 34) out = out "\\\""
          else if (b == 92) out = out "\\\\"
          else out = out sprintf("%c", b)
          continue
        }
        acc = sprintf("%c", b)
        lead = b
        seen2 = 0
        if (b >= 194 && b <= 223) need = 1
        else if (b >= 224 && b <= 239) need = 2
        else if (b >= 240 && b <= 244) need = 3
        else fail()
      }
    }
    END {
      if (bad || need != 0 || n < 1) print "null"
      else print "\"" out "\""
    }
  '
}

json_role_field() {
  r=$1
  case "$r" in
    dev|review) printf '"%s"' "$r" ;;
    *) printf 'null' ;;
  esac
}

json_verdict_field() {
  v=$1
  case "$v" in
    PASS|FAIL|UNKNOWN) printf '"%s"' "$v" ;;
    *) printf 'null' ;;
  esac
}

# 僅上報 PASS / FAIL / UNKNOWN（讀 REVIEW-round*.md 第一行 VERDICT:）
read_review_verdict() {
  wt=$1
  if [ -z "$wt" ]; then
    return
  fi
  base=$(basename "$wt")
  issue=$(printf '%s' "$base" | sed -n 's/^wt-\([0-9][0-9]*\).*/\1/p')
  if [ -z "$issue" ]; then
    return
  fi
  dir="$HOME/PunchClock-oki/shots${issue}-self"
  if [ ! -d "$dir" ]; then
    return
  fi
  latest=""
  for f in "$dir"/REVIEW-round*.md; do
    [ -f "$f" ] || continue
    latest=$f
  done
  if [ -z "$latest" ]; then
    return
  fi
  line=$(sed -n '1p' "$latest")
  verdict=$(printf '%s' "$line" | sed -n 's/^VERDICT:[[:space:]]*\([A-Z]*\).*/\1/p')
  case "$verdict" in
    PASS|FAIL|UNKNOWN) printf '%s' "$verdict" ;;
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
    part=$(printf '{"issue":%s,"round":%s,"branch":"%s","startedAt":%s,"role":"dev"%s%s}' \
      "$issue" "$round" "$branch" "$started" "$wt_json" "$commit_json")
    json="$json$sep$part"
    sep=","
  done <<EOF
$tsv
EOF
  json="$json]"
  RUNS_JSON="$json"
}

# 解析 ps 中的 chainmed / chainfast2 / loopfast（現行開發鏈）
read_chain_runs() {
  if [ -n "$MOCK_PS_CHAIN" ]; then
    ps_out="$MOCK_PS_CHAIN"
  elif [ -n "$MOCK_PS_OUTPUT" ]; then
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
    function add_issue(issue, started,    branch) {
      if (issue < 1) return
      branch = "ai/issue-" issue
      if (!(issue in best_started) || started < best_started[issue]) {
        best_started[issue] = started
        best_round[issue] = 0
        best_branch[issue] = branch
        best_wt[issue] = ""
      }
    }
    function after_script(rest, script,    pos, tail, tok) {
      pos = index(rest, script)
      if (pos < 1) return ""
      tail = substr(rest, pos + length(script))
      sub(/^[ \t]+/, "", tail)
      return tail
    }
    function loopfast_issue(rest,    tail, issue) {
      tail = after_script(rest, "loopfast.sh")
      if (tail == "") return 0
      if (match(tail, /^[0-9]+/)) return substr(tail, RSTART, RLENGTH) + 0
      return 0
    }
    function chainmed_issue(rest,    tail, n, i, seen_port) {
      tail = after_script(rest, "chainmed.sh")
      if (tail == "") return 0
      n = split(tail, parts, /[ \t]+/)
      seen_port = 0
      for (i = 1; i <= n; i++) {
        if (parts[i] ~ /^[0-9]+$/) {
          if (!seen_port) {
            seen_port = 1
            continue
          }
          return parts[i] + 0
        }
      }
      return 0
    }
    function chainfast_issues(line, started,    tail, n, i, tok) {
      tail = after_script(line, "chainfast2.sh")
      if (tail == "") return
      n = split(tail, parts, /[ \t]+/)
      if (n < 2) return
      for (i = 2; i <= n; i++) {
        tok = parts[i]
        sub(/\+.*/, "", tok)
        if (tok ~ /^[0-9]+$/) add_issue(tok + 0, started)
      }
    }
    BEGIN { count = 0 }
    {
      line = $0
      et = $1
      started = now_ms - etime_ms(et)
      if (line ~ /loopfast\.sh/) {
        issue = loopfast_issue(line)
        add_issue(issue, started)
        next
      }
      if (line ~ /chainmed\.sh/) {
        issue = chainmed_issue(line)
        add_issue(issue, started)
        next
      }
      if (line ~ /chainfast2\.sh/) {
        chainfast_issues(line, started)
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
  [ -z "$tsv" ] && return 0
  while IFS='	' read -r issue round branch started wt; do
    [ -z "$issue" ] && continue
    case "$RUNS_JSON" in
      *"\"issue\":$issue"*) continue ;;
    esac
    wt="$HOME/PunchClock-oki/wt-${issue}"
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
    part=$(printf '{"issue":%s,"round":%s,"branch":"%s","startedAt":%s,"role":"dev"%s%s}' \
      "$issue" "$round" "$branch" "$started" "$wt_json" "$commit_json")
    if [ "$RUNS_JSON" = "[]" ] || [ -z "$RUNS_JSON" ]; then
      RUNS_JSON="[$part]"
    else
      merged=$(printf '%s' "$RUNS_JSON" | sed 's/]$//')
      RUNS_JSON="${merged},${part}]"
    fi
  done <<EOF
$tsv
EOF
}

# 解析 ps 中的 run-review.sh <name> <prompt> <worktree> [model]
read_review_runs() {
  extra_json=""
  if [ -n "$MOCK_PS_REVIEW" ]; then
    ps_out="$MOCK_PS_REVIEW"
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
    function parse_name(name,    issue, role, round) {
      issue = 0; role = "dev"; round = 0
      if (match(name, /^run[0-9]+/)) {
        sub(/^run/, "", name)
        if (match(name, /^[0-9]+/)) {
          issue = substr(name, RSTART, RLENGTH) + 0
          name = substr(name, RSTART + RLENGTH)
        }
      }
      if (match(name, /^-[dvD]([0-9]+)/)) {
        letter = substr(name, RSTART + 1, 1)
        if (tolower(letter) == "v") role = "review"
        else role = "dev"
        round = substr(name, RSTART + 2, RLENGTH - 2) + 0
      } else if (match(name, /^-f([0-9]+)/)) {
        round = substr(name, RSTART + 2, RLENGTH - 2) + 0
        role = "dev"
      }
      return issue "|" role "|" round
    }
    BEGIN { count = 0 }
    {
      line = $0
      if (line !~ /run-review\.sh/) next
      et = $1
      started = now_ms - etime_ms(et)
      n = split(line, parts, /[ \t]+/)
      job = ""; wt = ""; model = ""
      for (i = 1; i <= n; i++) {
        if (parts[i] ~ /run-review\.sh$/) {
          if (i + 1 <= n) job = parts[i + 1]
          for (j = i + 2; j <= n; j++) {
            if (parts[j] ~ /^(\/|~)/) {
              wt = parts[j]
              if (j + 1 <= n && parts[j + 1] !~ /^(\/|~)/ && parts[j + 1] !~ /\.(md|txt|sh)$/) {
                model = parts[j + 1]
              }
              break
            }
          }
          break
        }
      }
      if (job == "") next
      split(parse_name(job), pr, /\|/)
      issue = pr[1] + 0
      role = pr[2]
      round = pr[3] + 0
      if (issue < 1) next
      branch = "ai/issue-" issue
      key = issue SUBSEP role
      if (!(key in best_started) || started > best_started[key]) {
        best_started[key] = started
        best_issue[key] = issue
        best_round[key] = round
        best_branch[key] = branch
        best_wt[key] = wt
        best_role[key] = role
        best_model[key] = model
      }
    }
    END {
      for (k in best_started) {
        if (count >= 12) continue
        printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
          best_issue[k], best_round[k], best_branch[k], best_started[k], best_wt[k], best_role[k], best_model[k]
        count++
      }
    }
  ')
  [ -z "$tsv" ] && return 0
  extra="["
  sep=""
  while IFS='	' read -r issue round branch started wt role model; do
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
    verdict=$(read_review_verdict "$wt")
    role_json=$(json_role_field "$role")
    model_json=$(json_string_field "$model")
    verdict_json=$(json_verdict_field "$verdict")
    part=$(printf '{"issue":%s,"round":%s,"branch":"%s","startedAt":%s,"role":%s,"model":%s,"verdict":%s%s%s}' \
      "$issue" "$round" "$branch" "$started" "$role_json" "$model_json" "$verdict_json" "$wt_json" "$commit_json")
    extra="$extra$sep$part"
    sep=","
  done <<EOF
$tsv
EOF
  extra="$extra]"
  if [ "$RUNS_JSON" = "[]" ] || [ -z "$RUNS_JSON" ]; then
    RUNS_JSON="$extra"
    return
  fi
  merged=$(printf '%s' "$RUNS_JSON" | sed 's/]$//')
  inner=$(printf '%s' "$extra" | sed 's/^\[//;s/\]$//')
  if [ -n "$inner" ]; then
    RUNS_JSON="${merged},${inner}]"
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
    return 0
  fi
  warn_perms
  url=$(pick_env DEV_STATUS_URL)
  if [ -z "$url" ]; then
    url="$DEFAULT_URL"
  fi
  token=$(pick_env DEV_STATUS_TOKEN)
  machine=$(pick_env DEV_STATUS_MACHINE)
  if [ -z "$token" ] || [ -z "$machine" ]; then
    return 0
  fi
  read_run
  read_wt_runs
  read_chain_runs
  read_review_runs
  read_waiter_queued
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
  apply_runs_lag
  apply_agent_lag
  issue_json=$(json_issue_field "$ISSUE")
  branch_json=$(json_branch_field "$BRANCH")
  started_json=$(json_started_field "$STARTED")
  runs_json="$RUNS_JSON"
  role_json="null"
  round_json="null"
  model_json="null"
  verdict_json="null"
  if [ -n "$ISSUE" ]; then
    wt_guess="$HOME/PunchClock-oki/wt-${ISSUE}"
    v=$(read_review_verdict "$wt_guess")
    verdict_json=$(json_verdict_field "$v")
  fi
  if [ "$RUNS_JSON" != "[]" ]; then
    primary=$(printf '%s' "$RUNS_JSON" | awk '
      function grab(s, key,    pat) {
        pat = "\"" key "\":"
        if (match(s, pat "[0-9]+")) return substr(s, RSTART + length(key) + 3, RLENGTH - length(key) - 3)
        if (match(s, pat "\"[^\"]+\"")) return substr(s, RSTART + length(key) + 3, RLENGTH - length(key) - 4)
        return ""
      }
      BEGIN { st = -1 }
      {
        s = $0
        if (match(s, /"startedAt":([0-9]+)/)) {
          t = substr(s, RSTART + 13, RLENGTH - 13) + 0
          if (st < 0 || t > st) {
            st = t
            role = grab(s, "role")
            round = grab(s, "round")
            model = grab(s, "model")
          }
        }
      }
      END {
        if (role != "") printf "%s|%s|%s", role, round, model
      }
    ')
    if [ -n "$primary" ]; then
      pr_role=$(printf '%s' "$primary" | cut -d'|' -f1)
      pr_round=$(printf '%s' "$primary" | cut -d'|' -f2)
      pr_model=$(printf '%s' "$primary" | cut -d'|' -f3)
      role_json=$(json_role_field "$pr_role")
      case "$pr_round" in
        ''|*[!0-9]*) round_json="null" ;;
        *) round_json="$pr_round" ;;
      esac
      model_json=$(json_string_field "$pr_model")
    fi
  fi
  display_name=$(pick_env DEV_STATUS_DISPLAY_NAME)
  location_tag=$(pick_env DEV_STATUS_LOCATION)
  display_json=$(json_display_field "$display_name")
  location_json=$(json_display_field "$location_tag")
  queued_json="${QUEUED_JSON:-[]}"
  payload=$(printf '{"machine":"%s","displayName":%s,"location":%s,"issue":%s,"branch":%s,"runStartedAt":%s,"agentRunning":%s,"role":%s,"round":%s,"model":%s,"verdict":%s,"runs":%s,"queued":%s%s%s}' \
    "$machine" "$display_json" "$location_json" "$issue_json" "$branch_json" "$started_json" "$agent" \
    "$role_json" "$round_json" "$model_json" "$verdict_json" \
    "$runs_json" "$queued_json" "$COMMIT_JSON" "$BATTERY_JSON")
  if [ -n "$RUNS_JSON" ] && [ "$RUNS_JSON" != "[]" ]; then
    now_ms=$(date +%s)000
    write_lag_file "$RUNS_JSON" "$agent" "$((now_ms + LAG_MS))"
  elif [ "$agent" = "true" ]; then
    now_ms=$(date +%s)000
    write_lag_file "$runs_json" "$agent" "$((now_ms + LAG_MS))"
  fi
  if ! command -v curl >/dev/null 2>&1; then
    return 0
  fi
  http_code=$(printf 'header = "Authorization: Bearer %s"\n' "$token" | curl -sS -K - \
    -o /dev/null \
    -w '%{http_code}' \
    --max-time 10 \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "$url" 2>/dev/null) || http_code="000"
  case "$http_code" in
    200|201|204) ;;
    *) echo "dev-heartbeat: POST $url failed http=${http_code:-unknown}" >&2 ;;
  esac
  return 0
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