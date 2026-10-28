#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
lock_ops
backup_now
