#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
remote_init "${1:?Usage: rollback.sh SERVER}"
job=$(ssh "${ssh_options[@]}" "$remote" 'sudo -n /usr/local/lib/workflow/release-job.sh start rollback') || { result=$?; printf '%s\n' 'Submission not confirmed; inspect release-job.sh follow latest on the server before retrying.' >&2; exit "$result"; }
follow_release "$job"
