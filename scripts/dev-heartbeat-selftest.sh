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

# --- chain dev ps parsing ---
MOCK_GIT_SHORT="beefcafe"
MOCK_PS_OUTPUT=""
MOCK_PS_CHAIN='  03:10 sh /path/chainmed.sh 9474 356
  02:05 sh /path/chainmed.sh 9475 348
  01:40 sh /path/loopfast.sh 351 needsha 9476
  00:50 sh /path/chainrun.sh 9476 med:351 355
  99:99 cursor-agent -p --model x run-wt.sh run888 fake'
RUNS_JSON="[]"
read_chain_runs
case "$RUNS_JSON" in
  *'"issue":356'*) ;;
  *) fail "chain runs json missing issue 356" ;;
esac
case "$RUNS_JSON" in
  *'"issue":348'*) ;;
  *) fail "chain runs json missing issue 348" ;;
esac
case "$RUNS_JSON" in
  *'"issue":351'*) ;;
  *) fail "chain runs json missing issue 351" ;;
esac
case "$RUNS_JSON" in
  *'"issue":888'*) fail "chain runs must ignore cursor-agent run-wt in prompt" ;;
esac
case "$RUNS_JSON" in
  *'"issue":355'*) fail "chain runs must not parse chainrun queued specs as active runs" ;;
esac
case "$RUNS_JSON" in
  *'"chain":"青龍"'*) ;;
  *) fail "chain runs json missing 青龍 for port 9474" ;;
esac
case "$RUNS_JSON" in
  *'"chain":"白虎"'*) ;;
  *) fail "chain runs json missing 白虎 for port 9475" ;;
esac
case "$RUNS_JSON" in
  *'"chain":"朱雀"'*) ;;
  *) fail "chain runs json missing 朱雀 for port 9476 loopfast" ;;
esac

MOCK_PS_CHAIN='  04:00 sh /path/chainmed-verify.sh 9475 735
  03:00 sh /path/chainrun-verify.sh 9474 734'
RUNS_JSON="[]"
read_chain_runs
case "$RUNS_JSON" in
  *'"issue":735'*) ;;
  *) fail "verify chain runs json missing issue 735" ;;
esac
case "$RUNS_JSON" in
  *'"issue":734'*) ;;
  *) fail "verify chain runs json missing issue 734" ;;
esac
case "$RUNS_JSON" in
  *'"issue":735'*'"chain":"白虎"'*) ;;
  *) fail "chainmed-verify must carry 白虎 chain name" ;;
esac
case "$RUNS_JSON" in
  *'"issue":734'*'"chain":"青龍"'*) ;;
  *) fail "chainrun-verify must carry 青龍 chain name" ;;
esac

MOCK_PS_CHAINS='sh /path/chainrun-verify.sh 9476 735'
dev_chains_running || fail "dev_chains_running should detect chainrun-verify"
unset MOCK_PS_CHAINS

MOCK_PS_CHAIN='  10:00 sh /path/chainfast2.sh 9475 683 680
  09:50 sh /path/loopfast.sh 683 needsha 9475'
RUNS_JSON="[]"
read_chain_runs
case "$RUNS_JSON" in
  *'"issue":683'*) ;;
  *) fail "chainfast2 runs must include active issue 683" ;;
esac
case "$RUNS_JSON" in
  *'"issue":680'*) fail "chainfast2 must not queue upcoming issue 680 as active run" ;;
esac

MOCK_PS_WAITERS='  10:00 sh /path/chainfast2.sh 9475 683 680'
RUNS_JSON='[{"issue":683,"startedAt":1000}]'
QUEUED_JSON="[]"
read_waiter_queued
case "$QUEUED_JSON" in
  *'"issue":680'*) ;;
  *) fail "chainfast2 queue json missing issue 680" ;;
esac
case "$QUEUED_JSON" in
  *'"afterIssue":683'*) ;;
  *) fail "chainfast2 queue must include afterIssue 683" ;;
esac
case "$QUEUED_JSON" in
  *'"issue":683'*) fail "chainfast2 queue must drop active run issue 683" ;;
esac

# --- waiter queue ps parsing ---
RUNS_JSON="[]"
MOCK_PS_WAITERS='  12:00 sh /path/chain-9477-after559-i566-waiter.sh
  01:00 sh chain-8000-after100-i200-waiter.sh'
read_waiter_queued
case "$QUEUED_JSON" in
  *'"issue":566'*) ;;
  *) fail "waiter queue json missing issue 566" ;;
esac
case "$QUEUED_JSON" in
  *'"afterIssue":559'*) ;;
  *) fail "waiter queue json missing afterIssue 559" ;;
esac

MOCK_PS_WAITERS='  01:00 sh /path/chain-9476-after581-i571-573-572-waiter.sh'
RUNS_JSON='[{"issue":571,"startedAt":1000}]'
QUEUED_JSON="[]"
read_waiter_queued
case "$QUEUED_JSON" in
  *'"issue":573'*) ;;
  *) fail "chain waiter queue json missing issue 573" ;;
esac
case "$QUEUED_JSON" in
  *'"issue":572'*) ;;
  *) fail "chain waiter queue json missing issue 572" ;;
esac
case "$QUEUED_JSON" in
  *'"issue":571'*) fail "chain waiter queue must drop active run issue 571" ;;
esac

MOCK_PS_WAITERS='  02:00 sh /path/i573-then-572-waiter.sh'
RUNS_JSON="[]"
QUEUED_JSON="[]"
read_waiter_queued
case "$QUEUED_JSON" in
  *'"issue":573'*) ;;
  *) fail "then-style waiter missing issue 573" ;;
esac
case "$QUEUED_JSON" in
  *'"issue":572'*) ;;
  *) fail "then-style waiter missing issue 572" ;;
esac

MOCK_DEV_CHAINS=1
dev_chains_running || fail "dev_chains_running should be true when mocked"
unset MOCK_DEV_CHAINS

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

# --- runs lag: 沿用不得反覆延後 until ---
lag_tmp=$(mktemp -d)
LAG_FILE="$lag_tmp/lag.json"
now_ms=$(date +%s)000
fixed_until=$((now_ms + 120000))
lag_runs='[{"issue":608,"branch":"ai/issue-608","startedAt":1000}]'
write_lag_file "$lag_runs" false "$fixed_until"
RUNS_JSON="[]"
RUNS_LIVE=false
agent=false
apply_runs_lag
case "$RUNS_JSON" in
  *'"issue":608'*) ;;
  *) fail "apply_runs_lag should carry runs while until valid" ;;
esac
if [ "$RUNS_LIVE" = "true" ] && [ -n "$RUNS_JSON" ] && [ "$RUNS_JSON" != "[]" ]; then
  write_lag_file "$RUNS_JSON" "$agent" "$((now_ms + LAG_MS))"
fi
until_after=$(sed -n 's/.*"until":\([0-9][0-9]*\).*/\1/p' "$LAG_FILE" | head -n 1)
assert_eq "$until_after" "$fixed_until" "carried runs must not extend lag until (beat 1)"
later_ms=$((now_ms + 60000))
RUNS_JSON="[]"
RUNS_LIVE=false
apply_runs_lag
if [ "$RUNS_LIVE" = "true" ] && [ -n "$RUNS_JSON" ] && [ "$RUNS_JSON" != "[]" ]; then
  write_lag_file "$RUNS_JSON" "$agent" "$((later_ms + LAG_MS))"
fi
until_after2=$(sed -n 's/.*"until":\([0-9][0-9]*\).*/\1/p' "$LAG_FILE" | head -n 1)
assert_eq "$until_after2" "$fixed_until" "carried runs must not extend lag until (beat 2)"

expired_until=$((now_ms - 1000))
write_lag_file "$lag_runs" false "$expired_until"
RUNS_JSON="[]"
apply_runs_lag
assert_eq "$RUNS_JSON" "[]" "runs empty after lag until expired"

MOCK_PS_OUTPUT='  01:00 sh /path/run-wt.sh run610 x ~/PunchClock-oki/wt-610'
MOCK_GIT_SHORT="abc1234"
read_wt_runs
RUNS_LIVE=false
if [ -n "$RUNS_JSON" ] && [ "$RUNS_JSON" != "[]" ]; then
  RUNS_LIVE=true
fi
agent=false
apply_runs_lag
case "$RUNS_JSON" in
  *'"issue":610'*) ;;
  *) fail "live ps runs missing issue 610" ;;
esac
until_live=$(sed -n 's/.*"until":\([0-9][0-9]*\).*/\1/p' "$LAG_FILE" | head -n 1)
if [ -z "$until_live" ] || [ "$until_live" -le "$fixed_until" ]; then
  fail "live runs should refresh lag until beyond stale fixed_until"
fi
MOCK_PS_OUTPUT=""
rm -rf "$lag_tmp"

# --- do_beat must return, not exit (so cmd_loop can continue) ---
in_do_beat=false
while IFS= read -r line; do
  if [ "$in_do_beat" = false ]; then
    case "$line" in
      do_beat\(\)) in_do_beat=true ;;
    esac
    continue
  fi
  case "$line" in
    \}) in_do_beat=false ;;
    *exit\ 0*) fail "do_beat must use return 0, not exit 0: $line" ;;
  esac
done < "$SCRIPT_DIR/dev-heartbeat.sh"

echo "dev-heartbeat-selftest: ok"
