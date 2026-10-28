#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
[[ -f /etc/workflow/env && ! -L /etc/workflow/env && $(stat -c '%u:%a' /etc/workflow/env) == '0:600' ]]
python3 - <<'PY'
import os,tempfile
from pathlib import Path
path=Path('/etc/workflow/env')
lines=[line for line in path.read_text().splitlines() if not line.startswith('INITIAL_ADMIN_PASSWORD=')]
fd,tmp=tempfile.mkstemp(dir=path.parent,prefix='.env.')
try:
    os.fchmod(fd,0o600)
    with os.fdopen(fd,'w') as f:f.write('\n'.join(lines)+'\n')
    os.chown(tmp,0,0);os.replace(tmp,path)
finally:
    if os.path.exists(tmp):os.unlink(tmp)
print('Initial password removed from file. Restart workflow to clear the process environment.')
PY
