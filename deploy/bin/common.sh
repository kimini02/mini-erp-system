#!/usr/bin/env bash
set +x
set -euo pipefail
remote_init() {
  target=${1:?server hostname required}
  [[ $target =~ ^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.duckdns\.org$ ]] || { printf '%s\n' 'Expected a DuckDNS hostname (subdomain.duckdns.org).' >&2;return 2; }
  user=${SSH_USER:-ubuntu}
  [[ $user =~ ^[a-z_][a-z0-9_-]*$ ]] || return 2
  remote="$user@$target"
  ssh_options=(-o BatchMode=yes -o StrictHostKeyChecking=yes)
  if [[ -n ${SSH_KEY:-} ]];then ssh_options+=(-i "$SSH_KEY");fi
}
