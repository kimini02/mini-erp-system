#!/usr/bin/env bash
set +x
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
source "$here/common.sh"
remote_init "${1:?Usage: deploy.sh SERVER}"
repo=$(git -C "$here/../.." rev-parse --show-toplevel)
cd "$repo"
[[ -z $(git status --porcelain --untracked-files=normal -- . ':(exclude).claude' ':(exclude).claude/**') ]] || { printf '%s\n' 'Commit or remove deployment-source changes before deploying.' >&2;exit 1; }
commit=$(git rev-parse --short=7 HEAD)
release="$(date -u +%Y%m%dT%H%M%SZ)-$commit"
work=$(mktemp -d)
remote_work=''
cleanup() {
  rm -rf -- "$work"
  if [[ -n $remote_work ]]; then ssh "${ssh_options[@]}" "$remote" "rm -rf -- '$remote_work'" || true;fi
}
trap cleanup EXIT
began=$SECONDS
(cd backend; ./mvnw -DskipTests package)
printf 'BUILD backend_s=%s\n' "$((SECONDS-began))"
began=$SECONDS
(cd frontend; npm ci; npm run build)
[[ -f frontend/dist/maintenance.html ]] || { printf '%s\n' 'M5 frontend maintenance.html is required before deployment.' >&2;exit 1; }
printf 'BUILD frontend_s=%s\n' "$((SECONDS-began))"
jar=$(find backend/target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar')
[[ -f $jar ]] || { printf '%s\n' 'Expected one executable jar.' >&2;exit 1; }
cp "$jar" "$work/app.jar";cp -R frontend/dist "$work/dist"
tar -czf "$work/release.tar.gz" -C "$work" app.jar dist
version=$(python3 - <<'PY'
from pathlib import Path
import re
versions=[]
for p in Path('backend/src/main/resources/db/migration').glob('V*__*.sql'):
 m=re.fullmatch(r'V(\d+)__.+\.sql',p.name)
 if not m:raise SystemExit('Non-integer migration version requires operator review.')
 versions.append(int(m[1]))
print(max(versions,default=0))
PY
)
remote_work=$(ssh "${ssh_options[@]}" "$remote" 'mktemp -d /tmp/workflow-upload.XXXXXX')
[[ $remote_work =~ ^/tmp/workflow-upload\.[A-Za-z0-9]+$ ]] || exit 1
began=$SECONDS
scp "${ssh_options[@]}" "$work/release.tar.gz" "$remote:$remote_work/release.tar.gz"
printf 'UPLOAD elapsed_s=%s release=%s\n' "$((SECONDS-began))" "$release"
ssh "${ssh_options[@]}" "$remote" "sudo -n /usr/local/lib/workflow/release.sh activate '$release' '$remote_work/release.tar.gz' '$version'"
