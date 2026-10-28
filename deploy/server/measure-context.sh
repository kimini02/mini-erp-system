#!/usr/bin/env bash
# Read-only context; no env/secrets file is loaded and no application job is run.
set +x
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_root
printf 'measured_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
awk '{printf "server_uptime_seconds=%.0f\nserver_uptime_hours=%.2f\n",$1,$1/3600}' /proc/uptime
if statuses=$(timeout 10 mariadb --protocol=socket --user=root --batch --skip-column-names -e "SHOW GLOBAL STATUS WHERE Variable_name IN ('Uptime','Innodb_buffer_pool_pages_data','Innodb_buffer_pool_pages_total','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests')" 2>/dev/null); then
  printf '%s\n' '[mariadb_global_status]'
  printf '%s\n' "$statuses"
else
  printf '%s\n' 'mariadb_status=확인 못 함 (socket connection/status query failed)'
fi
printf '%s\n' '[free_mib]'
free -m
printf '%s\n' '[swap_bytes]'
swapon --show --bytes --output NAME,TYPE,SIZE,USED,PRIO
awk '/^SwapTotal:|^SwapFree:/ {print}' /proc/meminfo
latest=''
if [[ -d /var/backups/workflow ]]; then
  while IFS= read -r file; do
    if [[ -f "$file.sha256" && -f "$file.version" ]]; then latest=$file; break; fi
  done < <(find /var/backups/workflow -maxdepth 1 -type f -name '*.sql.gz' -printf '%p\n' | LC_ALL=C sort -r)
fi
if [[ -n $latest ]]; then
  printf 'last_backup_file=%s\nlast_backup_utc=%s\n' "$(basename "$latest")" "$(date -u -r "$latest" +%Y-%m-%dT%H:%M:%SZ)"
else
  printf '%s\n' 'last_backup_utc=NONE'
fi
# The dedicated flock also covers pre-deploy and manual backups, independently of systemd.
if [[ -e /run/lock/workflow-backup.lock ]]; then
  exec 8</run/lock/workflow-backup.lock
  if flock -n -s 8; then printf '%s\n' 'backup_in_progress=NO'; flock -u 8;
  else printf '%s\n' 'backup_in_progress=YES'; fi
else
  printf '%s\n' 'backup_in_progress=NO (no backup lock yet)'
fi
if [[ -d /run/systemd/system ]] && command -v systemctl >/dev/null; then
  if state=$(systemctl show workflow-backup.service --property=ActiveState --value 2>/dev/null); then
    printf 'backup_service_state=%s\n' "$state"
  else printf '%s\n' 'backup_service_state=확인 못 함'; fi
else
  printf '%s\n' 'backup_service_state=확인 못 함 (systemd unavailable)'
fi
if [[ -L /opt/workflow/current ]]; then
  printf 'current_release=%s\n' "$(basename "$(readlink -f /opt/workflow/current)")"
else printf '%s\n' 'current_release=NONE'; fi
