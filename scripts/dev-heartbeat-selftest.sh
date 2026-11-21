#!/usr/bin/env sh
# 解析輔助函式自測（無網路、無 token）

set -e

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
DEV_HEARTBEAT_LIB=1
# shellcheck disable=SC1090
. "$SCRIPT_DIR/dev-heartbeat.sh"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_eq() {
  got=$1
  want=$2
  label=$3
  if [ "$got" != "$want" ]; then
    fail "$label: got '$got', want '$want'"
  fi
}

# --- battery % (item 2) ---
line1=' -InternalBattery-0 (id=1234)	85%; discharging; 3:20 remaining present: true'
assert_eq "$(parse_battery_pct "$line1")" "85" "pmset id=1234 tab line"
assert_eq "$(parse_battery_pct '100%; charged;')" "100" "100%; charged;"

# --- charging (item 3) ---
assert_eq "$(parse_battery_charging "$line1" "Now drawing from 'Battery Power'")" "false" "discharging"
assert_eq "$(parse_battery_charging '99%; charging; 1:00 remaining' "Now drawing from 'Battery Power'")" "true" "charging"
assert_eq "$(parse_battery_charging '100%; charged; (no estimate)' "Now drawing from 'Battery Power'")" "true" "charged"
assert_eq "$(parse_battery_charging '50%; discharging;' "Now drawing from 'AC Power'")" "true" "AC Power first line"

# discharging must not match *charging* wildcard
assert_eq "$(parse_battery_charging '80%; discharging; 2:00 remaining' "Now drawing from 'Battery Power'")" "false" "discharging not charging"

# --- JSON fields (item 5) ---
assert_eq "$(json_issue_field '127')" "127" "issue digits"
assert_eq "$(json_issue_field '12a7')" "null" "issue non-digit"
assert_eq "$(json_issue_field '')" "null" "issue empty"

assert_eq "$(json_started_field '1730000000000')" "1730000000000" "started digits"
assert_eq "$(json_started_field 'not-a-ts')" "null" "started invalid"

assert_eq "$(parse_run_started_ms '1730000000000')" "1730000000000" "run file epoch ms"
iso_ms=$(parse_run_started_ms '2024-06-01T00:00:00Z')
if [ -z "$iso_ms" ] || [ "$iso_ms" = "2024-06-01T00:00:00Z" ]; then
  fail "run file ISO 8601"
fi

assert_eq "$(json_branch_field 'ai/issue-127')" '"ai/issue-127"' "branch ok"
assert_eq "$(json_branch_field 'bad branch')" "null" "branch space"
assert_eq "$(json_branch_field '')" "null" "branch empty"

# --- display name / location (Unicode characters, not bytes) ---
repeat_char() {
  ch=$1
  count=$2
  out=""
  i=0
  while [ "$i" -lt "$count" ]; do
    out="${out}${ch}"
    i=$((i + 1))
  done
  printf '%s' "$out"
}

assert_display_cases() {
  loc=$1
  assert_eq "$(json_display_field '黑熊')" '"黑熊"' "$loc display 黑熊"
  assert_eq "$(json_display_field '家用房間')" '"家用房間"' "$loc location 家用房間"
  assert_eq "$(json_display_field '猛虎')" '"猛虎"' "$loc display 猛虎"
  assert_eq "$(json_display_field '家用')" '"家用"' "$loc location 家用"
  assert_eq "$(json_display_field '')" "null" "$loc display empty"
  assert_eq "$(json_display_field "$(printf '黑\n熊')")" "null" "$loc newline rejected"
  assert_eq "$(json_display_field "$(printf 'a\tb')")" "null" "$loc tab rejected"
  assert_eq "$(json_display_field "$(printf 'a\rb')")" "null" "$loc cr rejected"
  assert_eq "$(json_display_field "$(printf 'a\177b')")" "null" "$loc del rejected"
  name24=$(repeat_char '熊' 24)
  name25=$(repeat_char '熊' 25)
  assert_eq "$(json_display_field "$name24")" "\"${name24}\"" "$loc 24 han chars"
  assert_eq "$(json_display_field "$name25")" "null" "$loc 25 han chars"
  # 9 個中文字是 27 bytes，仍須通過（上限以字元計）
  name9=$(repeat_char '熊' 9)
  assert_eq "$(json_display_field "$name9")" "\"${name9}\"" "$loc 9 han chars over 24 bytes"
  assert_eq "$(json_display_field 'abcdefghijklmnopqrstuvwx')" '"abcdefghijklmnopqrstuvwx"' "$loc 24 ascii"
  assert_eq "$(json_display_field 'abcdefghijklmnopqrstuvwxy')" "null" "$loc 25 ascii"
  assert_eq "$(json_display_field 'a"b')" '"a\"b"' "$loc escape quote"
  assert_eq "$(json_display_field 'a\b')" '"a\\b"' "$loc escape backslash"
  assert_eq "$(json_display_field 'a\"b')" '"a\\\"b"' "$loc escape quote and backslash"
  display_json=$(json_display_field '黑熊')
  location_json=$(json_display_field '家用房間')
  payload=$(printf '{"displayName":%s,"location":%s}' "$display_json" "$location_json")
  assert_eq "$payload" '{"displayName":"黑熊","location":"家用房間"}' "$loc assembled han payload"
  display_json=$(json_display_field 'say "hi"')
  payload=$(printf '{"displayName":%s}' "$display_json")
  assert_eq "$payload" '{"displayName":"say \"hi\""}' "$loc assembled escaped quote"
  display_json=$(json_display_field 'a\b')
  payload=$(printf '{"displayName":%s}' "$display_json")
  assert_eq "$payload" '{"displayName":"a\\b"}' "$loc assembled escaped backslash"
  display_json=$(json_display_field "$(printf '猛\n虎')")
  payload=$(printf '{"displayName":%s}' "$display_json")
  assert_eq "$payload" '{"displayName":null}' "$loc assembled control is null"
}

saved_lc_all=${LC_ALL-}
LC_ALL=C
export LC_ALL
assert_display_cases "C"
if printf '測' | LC_ALL=en_US.UTF-8 wc -m 2>/dev/null | awk '{exit !($1==1)}'; then
  LC_ALL=en_US.UTF-8
  export LC_ALL
  assert_display_cases "en_US.UTF-8"
fi
if printf '測' | LC_ALL=C.UTF-8 wc -m 2>/dev/null | awk '{exit !($1==1)}'; then
  LC_ALL=C.UTF-8
  export LC_ALL
  assert_display_cases "C.UTF-8"
fi
if [ -n "$saved_lc_all" ]; then
  LC_ALL=$saved_lc_all
  export LC_ALL
else
  unset LC_ALL
fi

# --- etime (item 4) ---
etime_ms() {
  printf '%s' "$1" | awk '
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
    { print etime_ms($0) }
  '
}

assert_eq "$(etime_ms '12:34')" "754000" "etime mm:ss"
assert_eq "$(etime_ms '1:05:07')" "3907000" "etime hh:mm:ss"
assert_eq "$(etime_ms '1-02:03:04')" "93784000" "etime dd-hh:mm:ss"

# --- run-wt ps parsing (item 5b) ---
MOCK_GIT_SHORT="deadbeef"
MOCK_PS_OUTPUT='  12:34 sh /path/run-wt.sh run235-f2 prompt ~/PunchClock-oki/wt-235
  05:10 sh run-wt.sh run237 other ~/PunchClock-oki/wt-237
  99:99 cursor-agent --fix run-wt.sh run999-f3 in prompt'
read_wt_runs
case "$RUNS_JSON" in
  *'"issue":235'*) ;;
  *) fail "runs json missing issue 235" ;;
esac
case "$RUNS_JSON" in
  *'"round":2'*) ;;
  *) fail "runs json missing round 2 for run235-f2" ;;
esac
case "$RUNS_JSON" in
  *'"round":0'*) ;;
  *) fail "runs json missing round 0 for run237" ;;
esac
assert_eq "$(pick_longest_run_from_json "$RUNS_JSON" | cut -d'|' -f1)" "235" "longest run issue from json"
case "$RUNS_JSON" in
  *'"issue":999'*) fail "runs json must ignore cursor-agent prompt run-wt.sh run999-f3" ;;
esac

# --- run-review ps parsing ---
MOCK_GIT_SHORT="cafebabe"
MOCK_PS_REVIEW='  08:00 sh /path/run-review.sh run254-v2 prompt.md ~/PunchClock-oki/wt-254 claude-opus-5-thinking-high
  20:10 sh run-review.sh run247-d1 p ~/PunchClock-oki/wt-247'
RUNS_JSON="[]"
read_review_runs
case "$RUNS_JSON" in
  *'"role":"review"'*) ;;
  *) fail "review runs missing role review" ;;
esac
case "$RUNS_JSON" in
  *'"issue":254'*) ;;
  *) fail "review runs missing issue 254" ;;
esac
case "$RUNS_JSON" in
  *'"round":2'*) ;;
  *) fail "review runs missing round 2" ;;
esac

assert_eq "$(json_role_field 'dev')" '"dev"' "json role dev"
assert_eq "$(json_role_field 'nope')" "null" "json role bad"
assert_eq "$(json_verdict_field 'PASS')" '"PASS"' "json verdict"
# 本機 shots 仍保留；CI 沒有這份目錄時改走下面的暫存 HOME。
if [ -d "$HOME/PunchClock-oki/shots247-self" ]; then
  assert_eq "$(read_review_verdict "$HOME/PunchClock-oki/wt-247")" "FAIL" "read verdict from shots247-self"
fi
prev_home=$HOME
mock_home=$(mktemp -d)
mkdir -p "$mock_home/PunchClock-oki/shots247-self"
printf 'VERDICT: FAIL\n' > "$mock_home/PunchClock-oki/shots247-self/REVIEW-round1.md"
HOME=$mock_home
assert_eq "$(read_review_verdict "$mock_home/PunchClock-oki/wt-247")" "FAIL" "read verdict from mock shots"
printf 'VERDICT: PASS\nnotes\n' > "$mock_home/PunchClock-oki/shots247-self/REVIEW-round2.md"
assert_eq "$(read_review_verdict "$mock_home/PunchClock-oki/wt-247")" "PASS" "read latest mock verdict"
HOME=$prev_home
rm -rf "$mock_home"

echo "dev-heartbeat-selftest: ok"
