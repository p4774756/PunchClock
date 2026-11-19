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

echo "dev-heartbeat-selftest: ok"