#!/usr/bin/env sh
# 標記本機正在處理哪張 issue（寫入 ~/.punchclock/dev-status-run，供 dev-heartbeat 回報）。
#
# 本機 run 腳本應在 agent／工作流程開始前執行：
#   /path/to/PunchClock/scripts/dev-run-mark.sh start <issue> <branch>
# 結束時執行 stop；失敗或中斷時也要 stop，建議在 run 腳本用 trap：
#   trap '/path/to/PunchClock/scripts/dev-run-mark.sh stop' EXIT
#
# 用法：start <issue> <branch> | stop

set -e

RUN_FILE="${DEV_STATUS_RUN:-$HOME/.punchclock/dev-status-run}"

cmd_start() {
  issue=$1
  branch=$2
  if [ -z "$issue" ] || [ -z "$branch" ]; then
    echo "用法：$0 start <issue> <branch>" >&2
    exit 1
  fi
  mkdir -p "$(dirname "$RUN_FILE")"
  started=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  printf '%s\n%s\n%s\n' "$issue" "$branch" "$started" > "$RUN_FILE"
}

cmd_stop() {
  rm -f "$RUN_FILE" 2>/dev/null || true
}

case "$1" in
  start) cmd_start "$2" "$3" ;;
  stop) cmd_stop ;;
  *)
    echo "用法：$0 start <issue> <branch> | stop" >&2
    exit 1
    ;;
esac