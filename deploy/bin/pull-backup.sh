#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
remote_init "${1:?Usage: pull-backup.sh SERVER}"
umask 077
mkdir -p "$HOME/workflow-backups/$target"
work=$(mktemp -d "$HOME/workflow-backups/$target/.pull.XXXXXX")
trap 'rm -rf -- "$work"' EXIT
# Stream root-owned backup files through the SSH channel, never chmod them world-readable.
ssh "${ssh_options[@]}" "$remote" 'sudo -n bash -s' >"$work/bundle.tar" <<'REMOTE'
set -euo pipefail
source /usr/local/lib/workflow/common.sh
lock_ops
file=$(latest_backup)
[[ -n $file && -f /var/backups/workflow/$file.sha256 ]]
cd /var/backups/workflow
files=("$file" "$file.sha256" "$file.version")
[[ ! -f $file.counts.tsv ]] || files+=("$file.counts.tsv")
tar -cf - -- "${files[@]}"
REMOTE
python3 - "$work" <<'PY'
import sys,tarfile,hashlib,shutil
from pathlib import Path
work=Path(sys.argv[1]);destination=work.parent
with tarfile.open(work/'bundle.tar') as tar:
 for member in tar.getmembers():
  if not member.isfile() or '/' in member.name or member.name.startswith('.'):raise SystemExit('Invalid backup bundle.')
  with tar.extractfile(member) as source, (work/member.name).open('wb') as target: shutil.copyfileobj(source,target)
files=list(work.glob('*.sql.gz'))
if len(files)!=1:raise SystemExit('Expected one backup.')
backup=files[0];expected=(work/(backup.name+'.sha256')).read_text().split()[0]
digest=hashlib.sha256()
with backup.open('rb') as stream:
 for chunk in iter(lambda:stream.read(1024*1024),b''):digest.update(chunk)
if digest.hexdigest()!=expected:raise SystemExit('Checksum mismatch.')
for p in work.iterdir():
 if p.name!='bundle.tar':p.chmod(0o600);p.replace(destination/p.name)
print('BACKUP_DOWNLOADED checksum=MATCH destination='+str(destination/backup.name))
PY
