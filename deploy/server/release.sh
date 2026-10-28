#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
lock_ops
redacted_logs() {
  load_env || { printf "%s\n" "Logs withheld: secret redaction environment unavailable." >&2; return 0; }
  journalctl -u workflow --no-pager -n 50 -o cat | python3 -c '
import os,re,sys
values=[v for k,v in os.environ.items() if any(x in k for x in ["PASSWORD","JWT_SECRET","WEBHOOK","INITIAL_ADMIN","DUCKDNS_TOKEN"]) and v]
for line in sys.stdin:
 for value in values:line=line.replace(value,"[REDACTED]")
 line=re.sub(r"(?i)(Bearer\s+|setupToken=|resetToken=)[^\s&\"<>]+",r"\1[REDACTED]",line)
 print(line,end="")'
}
prune() {
  python3 - <<'PY'
from pathlib import Path
import shutil,re
app=Path('/opt/workflow');web=Path('/var/www/workflow')
current=app.joinpath('current').resolve().name
previous=Path('/etc/workflow/previous-release').read_text().strip() if Path('/etc/workflow/previous-release').exists() else ''
names=sorted((p.name for p in (app/'releases').iterdir() if p.is_dir() and re.fullmatch(r'\d{8}T\d{6}Z-[0-9a-f]{7}',p.name)),reverse=True)
keep={n for n in [current,previous] if n in names}
for name in names:
 if len(keep)<3:keep.add(name)
for name in names:
 if name not in keep:
  for base in [app,web]:
   path=base/'releases'/name
   if path.exists():shutil.rmtree(path)
print('RETAIN releases='+str(len(keep)))
PY
}
command=${1:-};shift || true
case $command in
  activate)
    release=${1:?release required};archive=${2:?archive required};local_version=${3:?migration version required}
    valid_release "$release"; [[ $local_version =~ ^[0-9]+$ && -f $archive ]]
    now=$(TZ=Asia/Seoul date +%H%M)
    [[ $now < 0330 || $now > 0339 ]] || { printf '%s\n' 'Do not deploy in the 03:30-03:39 KST backup window.' >&2; exit 1; }
    current_version=$(schema_version)
    [[ $current_version =~ ^[0-9]+$ ]] || { printf '%s\n' 'Non-integer migration version requires operator review.' >&2; exit 1; }
    if ((local_version>current_version)); then backup_now;fi
    [[ ! -e /opt/workflow/releases/$release && ! -e /var/www/workflow/releases/$release ]] || { printf '%s\n' 'Release already exists.' >&2;exit 1; }
    stage=$(mktemp -d /opt/workflow/.stage.XXXXXX)
    trap 'rm -rf -- "$stage"' EXIT
    python3 - "$archive" "$stage" <<'PY'
import tarfile,sys
from pathlib import PurePosixPath
with tarfile.open(sys.argv[1],'r:gz') as tar:
 for member in tar.getmembers():
  path=PurePosixPath(member.name)
  if path.is_absolute() or '..' in path.parts or not (member.isfile() or member.isdir()):raise SystemExit('Unsafe release archive.')
 tar.extractall(sys.argv[2],filter='data')
PY
    [[ -f $stage/app.jar && -f $stage/dist/index.html && -f $stage/dist/maintenance.html ]]
    install -d -m 755 "/opt/workflow/releases/$release" "/var/www/workflow/releases/$release"
    install -m 644 "$stage/app.jar" "/opt/workflow/releases/$release/app.jar"
    cp -R "$stage/dist/." "/var/www/workflow/releases/$release/"
    chown -R root:root "/opt/workflow/releases/$release" "/var/www/workflow/releases/$release"
    find "/var/www/workflow/releases/$release" -type d -exec chmod 755 {} +
    find "/var/www/workflow/releases/$release" -type f -exec chmod 644 {} +
    old=''; if [[ -L /opt/workflow/current ]]; then old=$(basename "$(readlink -f /opt/workflow/current)");valid_release "$old";fi
    began=$SECONDS
    point_link /opt/workflow "$release"
    if systemctl restart workflow && health_wait; then
      point_link /var/www/workflow "$release"
      install -m 644 "$stage/dist/maintenance.html" /var/www/workflow/maintenance.html
      printf '%s\n' "$old" >/etc/workflow/previous-release
      printf 'ACTIVATE release=%s health_elapsed_s=%s\n' "$release" "$((SECONDS-began))"
      journalctl -u workflow --no-pager -n 100 -o cat | grep -E 'Started BackendApplication in [0-9.]+ seconds' | tail -1 || true
      prune
    else
      printf 'ACTIVATE_FAILED release=%s rollback=%s\n' "$release" "${old:-NONE}" >&2
      redacted_logs || true
      if [[ -n $old ]]; then
        point_link /opt/workflow "$old"
        if systemctl restart workflow && health_wait;then printf 'ROLLBACK_OK release=%s\n' "$old";else printf '%s\n' 'ROLLBACK_FAILED; stop and inspect schema compatibility/backup.' >&2;fi
      else systemctl stop workflow;rm -f /opt/workflow/current;fi
      exit 1
    fi
    ;;
  rollback)
    previous=$(cat /etc/workflow/previous-release);valid_release "$previous"
    [[ -f /opt/workflow/releases/$previous/app.jar && -f /var/www/workflow/releases/$previous/index.html ]]
    current=$(basename "$(readlink -f /opt/workflow/current)");valid_release "$current"
    point_link /opt/workflow "$previous"
    if systemctl restart workflow && health_wait; then
      point_link /var/www/workflow "$previous"
      printf '%s\n' "$current" >/etc/workflow/previous-release
      printf 'ROLLBACK_OK release=%s\n' "$previous"
    else
      redacted_logs || true
      point_link /opt/workflow "$current"
      systemctl restart workflow && health_wait || true
      printf '%s\n' 'ROLLBACK_FAILED; original backend link restored. Inspect schema compatibility.' >&2
      exit 1
    fi
    ;;
  *) printf '%s\n' 'Usage: release.sh activate RELEASE ARCHIVE MAX_VERSION | rollback' >&2;exit 2;;
esac
