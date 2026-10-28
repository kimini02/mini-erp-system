#!/usr/bin/env bash
set +x
set -euo pipefail
remote_init() {
  target=${1:?server hostname required}
  [[ $target =~ ^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.duckdns\.org$ ]] || { printf '%s\n' 'Expected a DuckDNS hostname (subdomain.duckdns.org).' >&2;return 2; }
  user=${SSH_USER:-ubuntu}
  [[ $user =~ ^[a-z_][a-z0-9_-]*$ ]] || return 2
  remote="$user@$target"
  ssh_options=(-o BatchMode=yes -o StrictHostKeyChecking=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=4)
  if [[ -n ${SSH_KEY:-} ]];then ssh_options+=(-i "$SSH_KEY");fi
}

follow_release() {
  local job=$1 result
  [[ $job =~ ^(activate|rollback)-[0-9]{8}T[0-9]{6}Z-[A-Za-z0-9]{8}$ ]] || return 2
  printf 'RELEASE_JOB id=%s log=/var/lib/workflow-release/%s/run.log\n' "$job" "$job"
  if ssh "${ssh_options[@]}" "$remote" "sudo -n /usr/local/lib/workflow/release-job.sh follow '$job'"; then return 0;
  else
    result=$?
    if ((result==255)); then printf '%s\n' 'Connection lost; the server job continues. Reconnect and follow this job before retrying.' >&2; fi
    return "$result"
  fi
}
