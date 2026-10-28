#!/usr/bin/env bash
set +x
set -euo pipefail
file=${1:?Usage: restore-local.sh BACKUP.sql.gz}
[[ -f $file.sha256 && -f $file.version && -f $file.counts.tsv ]] || { printf '%s\n' 'Need checksum, version and counts from server restore rehearsal.' >&2;exit 1; }
python3 - "$file" <<'PY'
import hashlib,sys
from pathlib import Path
p=Path(sys.argv[1]);expected=Path(str(p)+'.sha256').read_text().split()[0]
digest=hashlib.sha256()
with p.open('rb') as stream:
 for chunk in iter(lambda:stream.read(1024*1024),b''):digest.update(chunk)
if digest.hexdigest()!=expected:raise SystemExit('Checksum mismatch.')
PY
work=$(mktemp -d)
container=''
cleanup() { [[ -z $container ]] || docker rm -f "$container" >/dev/null;rm -rf -- "$work"; }
trap cleanup EXIT
# Disposable offline recovery only: no network, host port or password in logs/argv.
container=$(docker run -d --network none -e MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=1 mariadb:10.11)
ready=0
for ((n=0;n<60;n++));do if docker exec "$container" mariadb-admin --protocol=socket --user=root ping --silent >/dev/null 2>&1;then ready=1;break;fi;sleep 1;done
((ready)) || { printf '%s\n' 'Recovery container did not start.' >&2;exit 1; }
began=$SECONDS
docker exec "$container" mariadb --protocol=socket --user=root -e 'CREATE DATABASE mini_erp_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
gzip -dc "$file" | docker exec -i "$container" mariadb --protocol=socket --user=root mini_erp_restore
while IFS=$'\t' read -r table expected;do
 [[ $table =~ ^[A-Za-z0-9_]+$ && $expected =~ ^[0-9]+$ ]] || exit 1
 count=$(docker exec "$container" mariadb --protocol=socket --user=root --batch --skip-column-names mini_erp_restore -e "SELECT COUNT(*) FROM \`$table\`")
 printf '%s\t%s\n' "$table" "$count" >>"$work/counts.tsv"
done <"$file.counts.tsv"
diff -u "$file.counts.tsv" "$work/counts.tsv"
version=$(docker exec "$container" mariadb --protocol=socket --user=root --batch --skip-column-names mini_erp_restore -e 'SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1')
[[ $version == "$(cat "$file.version")" ]]
printf 'LOCAL_RESTORE tables=%s flyway=%s counts=MATCH elapsed_s=%s\n' "$(wc -l <"$work/counts.tsv")" "$version" "$((SECONDS-began))"
