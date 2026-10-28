#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
load_env
[[ -n ${DISCORD_WEBHOOK_URL:-} ]] || exit 0
# App Clock is Seoul; absence's target is yesterday even though it runs today at 00:10.
today=$(TZ=Asia/Seoul date +%F)
yesterday=$(TZ=Asia/Seoul date -d yesterday +%F)
for job in ATTENDANCE_ABSENCE TASK_OVERDUE; do
  target=$today; [[ $job != ATTENDANCE_ABSENCE ]] || target=$yesterday
  status=$(db mini_erp -e "SELECT status FROM batch_runs WHERE job_name='$job' AND target_date='$target' AND started_at>='$today 00:00:00' ORDER BY id DESC LIMIT 1")
  if [[ $status != SUCCESS ]]; then "$(dirname "$(readlink -f "$0")")/notify.sh" "$job"; fi
 done
