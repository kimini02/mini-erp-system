#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
[[ -f /etc/workflow/env ]] || exit 0
load_env
[[ -n ${DISCORD_WEBHOOK_URL:-} ]] || exit 0
reason=${1:-workflow.service}
[[ $reason =~ ^[A-Za-z0-9_.@:-]+$ ]] || exit 1
# curl receives URL through stdin; process listings and error logs contain no webhook token.
{ printf 'url = "%s"\n' "$DISCORD_WEBHOOK_URL"; printf 'header = "Content-Type: application/json"\n'; printf 'data = "{\\"content\\":\\"WorkFlow failure: %s\\"}"\n' "$reason"; } |
  curl --config - --silent --fail --output /dev/null --connect-timeout 3 --max-time 8 2>/dev/null || { printf '%s\n' 'Optional failure notification could not be delivered.' >&2; exit 1; }
