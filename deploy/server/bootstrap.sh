#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
here=$(dirname "$(readlink -f "$0")")
no_systemd=0
domain=''
while (($#)); do
  case $1 in
    --no-systemd) no_systemd=1; shift;;
    --domain) domain=${2:?hostname required}; shift 2;;
    *) printf '%s\n' 'Usage: bootstrap.sh [--no-systemd] --domain HOSTNAME' >&2; exit 2;;
  esac
done
if [[ -z $domain && -f /etc/workflow/domain ]]; then domain=$(cat /etc/workflow/domain); fi
[[ $domain =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ && $domain == *.* && $domain != *..* ]] || { printf '%s\n' 'A DNS hostname is required.' >&2; exit 2; }
source /etc/os-release
[[ $ID == ubuntu && $VERSION_ID == 24.04 ]] || { printf '%s\n' 'Ubuntu 24.04 is required.' >&2; exit 1; }
# Serialize package-triggered restarts, config placement and explicit restarts with backups/deploys.
lock_ops
export DEBIAN_FRONTEND=noninteractive
# Avoid service start attempts only in the disposable, non-systemd verification environment.
policy_created=0
cleanup_policy() { if ((policy_created)); then rm -f /usr/sbin/policy-rc.d; fi; }
trap cleanup_policy EXIT
if ((no_systemd)) && [[ ! -e /usr/sbin/policy-rc.d ]]; then printf '#!/bin/sh\nexit 101\n' >/usr/sbin/policy-rc.d;chmod 755 /usr/sbin/policy-rc.d;policy_created=1; fi
apt-get update -qq
apt-get install -y -qq openjdk-17-jre-headless mariadb-server redis-server nginx certbot sysstat jq python3 openssl curl rsync ca-certificates logrotate util-linux openssh-client
getent group workflow >/dev/null || groupadd --system workflow
id workflow >/dev/null 2>&1 || useradd --system --gid workflow --home-dir /var/lib/workflow --shell /usr/sbin/nologin workflow
install -d -o root -g root -m 755 /opt/workflow/releases /var/www/workflow/releases /var/www/letsencrypt /usr/local/lib/workflow /etc/nginx/snippets/workflow
install -d -o root -g root -m 700 /etc/workflow /var/backups/workflow
install -d -o workflow -g workflow -m 700 /var/lib/workflow
mkdir -p /var/log/mysql
install -d -o mysql -g adm -m 750 /var/log/mysql/workflow
printf '%s\n' "$domain" >/etc/workflow/domain
chmod 644 /etc/workflow/domain
# Keep OS/DB SYSTEM time UTC; application business time remains Clock(Asia/Seoul).
ln -snf /usr/share/zoneinfo/UTC /etc/localtime
printf 'Etc/UTC\n' >/etc/timezone
printf 'vm.swappiness=10\n' >/etc/sysctl.d/60-workflow.conf
for script in "$here"/*.sh; do install -o root -g root -m 755 "$script" /usr/local/lib/workflow/; done
install -m 644 "$here/mariadb-60-workflow.cnf" /etc/mysql/mariadb.conf.d/60-workflow.cnf
install -m 644 "$here/redis-workflow.conf" /etc/redis/workflow.conf
# Include last so packaged defaults cannot override loopback/memory settings.
python3 - <<'PY'
from pathlib import Path
p=Path('/etc/redis/redis.conf')
s='\n'.join(line for line in p.read_text().splitlines() if line!='include /etc/redis/workflow.conf')
p.write_text(s.rstrip()+'\n\ninclude /etc/redis/workflow.conf\n')
PY
mkdir -p /etc/systemd/journald.conf.d /etc/systemd/system /etc/letsencrypt/renewal-hooks/deploy /etc/ssh/sshd_config.d
install -m 644 "$here/journald-workflow.conf" /etc/systemd/journald.conf.d/60-workflow.conf
for unit in "$here"/*.service "$here"/*.timer; do install -m 644 "$unit" /etc/systemd/system/; done
install -m 644 "$here/logrotate-workflow" /etc/logrotate.d/workflow
install -m 644 "$here/nginx-observability.conf" /etc/nginx/conf.d/workflow-observability.conf
for snippet in "$here"/nginx-snippets/*.conf; do install -m 644 "$snippet" /etc/nginx/snippets/workflow/; done
install -m 644 "$here/maintenance.html" /var/www/workflow/maintenance.html
if [[ -f /etc/letsencrypt/live/$domain/fullchain.pem && -f /etc/letsencrypt/live/$domain/privkey.pem ]]; then template=nginx-workflow.conf; else template=nginx-http-bootstrap.conf; fi
sed "s/CHANGE_ME/$domain/g" "$here/$template" >/etc/nginx/sites-available/workflow
ln -snf /etc/nginx/sites-available/workflow /etc/nginx/sites-enabled/workflow
rm -f /etc/nginx/sites-enabled/default
printf '#!/bin/sh\n/usr/sbin/nginx -t && /bin/systemctl reload nginx\n' >/etc/letsencrypt/renewal-hooks/deploy/workflow
chmod 755 /etc/letsencrypt/renewal-hooks/deploy/workflow
printf 'PermitRootLogin no\nPasswordAuthentication no\n' >/etc/ssh/sshd_config.d/00-workflow.conf
sed -i 's/^ENABLED=.*/ENABLED="true"/' /etc/default/sysstat
mkdir -p /etc/systemd/system/sysstat-collect.timer.d
printf '[Timer]\nOnCalendar=\nOnCalendar=*-*-* *:*:00\n' >/etc/systemd/system/sysstat-collect.timer.d/workflow.conf
nginx -t
if ((no_systemd)); then
  printf '%s\n' 'NO_SYSTEMD: packages/users/directories/configs installed; swap/sysctl/services/timers/DB grants deferred.'
  exit 0
fi
if [[ ! -e /swapfile ]]; then fallocate -l 2G /swapfile;chmod 600 /swapfile;mkswap /swapfile >/dev/null;fi
[[ $(stat -c %a /swapfile) == 600 ]] || chmod 600 /swapfile
swapon --show=NAME --noheadings | grep -Fxq /swapfile || swapon /swapfile
grep -Eq '^/swapfile[[:space:]]' /etc/fstab || printf '/swapfile none swap sw 0 0\n' >>/etc/fstab
sysctl --system >/dev/null
systemctl daemon-reload
systemctl enable mariadb redis-server nginx sysstat sysstat-collect.timer certbot.timer workflow-backup.timer batch-check.timer workflow duckdns-update.service
systemctl restart mariadb redis-server nginx systemd-journald
if [[ -f /etc/workflow/env ]]; then "$here/provision-db.sh"; fi
# Apply SSH policy before a potentially failing external DNS update.
if command -v sshd >/dev/null; then sshd -t;sshd -T | grep -E '^(permitrootlogin|passwordauthentication) ';systemctl reload ssh;fi
flock -u 9
exec 9>&-
# Persistent timers may launch jobs that need the same maintenance lock.
systemctl start sysstat-collect.timer certbot.timer
if [[ -f /etc/workflow/env ]]; then
  systemctl start workflow-backup.timer batch-check.timer
  if ! systemctl restart duckdns-update.service; then
    printf '%s\n' 'DuckDNS update failed after SSH policy was applied. Inspect: sudo journalctl -u duckdns-update.service -n 50 --no-pager' >&2
    printf '%s\n' 'After fixing the cause: sudo systemctl reset-failed duckdns-update.service; sudo systemctl restart duckdns-update.service' >&2
    exit 1
  fi
else printf '%s\n' 'Next: run setup-secrets.sh interactively, then bootstrap.sh again.'; fi
printf '%s\n' 'Bootstrap complete. Certificate issuance and release activation are separate operator steps.'
