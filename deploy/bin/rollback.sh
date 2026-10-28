#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
remote_init "${1:?Usage: rollback.sh SERVER}"
ssh "${ssh_options[@]}" "$remote" 'sudo -n /usr/local/lib/workflow/release.sh rollback'
