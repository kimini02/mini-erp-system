#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
lock_ops
file=${1:-/var/backups/workflow/$(latest_backup)}
[[ -f $file && -f $file.sha256 ]] || { printf '%s\n' 'Backup and checksum required.' >&2; exit 1; }
(cd "$(dirname "$file")"; sha256sum --check "$(basename "$file").sha256")
[[ $(db -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='mini_erp_restore'") == 0 ]] || { printf '%s\n' 'mini_erp_restore already exists; refusing to overwrite it.' >&2; exit 1; }
created=0
work=$(mktemp -d)
cleanup() { if ((created)); then db -e 'DROP DATABASE mini_erp_restore'; fi; rm -rf -- "$work"; }
trap cleanup EXIT
began=$SECONDS
db -e 'CREATE DATABASE mini_erp_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
created=1
gzip -dc "$file" | db mini_erp_restore >/dev/null
for database in mini_erp mini_erp_restore; do
  while IFS= read -r table; do
    [[ $table =~ ^[A-Za-z0-9_]+$ ]] || exit 1
    printf '%s\t%s\n' "$table" "$(db "$database" -e "SELECT COUNT(*) FROM \`$table\`")"
  done < <(db -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$database' AND table_type='BASE TABLE' ORDER BY table_name") >"$work/$database.tsv"
done
if ! diff -u "$work/mini_erp.tsv" "$work/mini_erp_restore.tsv"; then printf '%s\n' 'Counts differ. Run during a write-free window; no production tables were changed.' >&2; exit 1; fi
original=$(schema_version)
restored=$(schema_version mini_erp_restore)
[[ $original == "$restored" ]] || { printf 'Flyway differs: source=%s restored=%s\n' "$original" "$restored" >&2; exit 1; }
install -m 600 "$work/mini_erp_restore.tsv" "$file.counts.tsv"
printf 'RESTORE tables=%s flyway=%s counts=MATCH elapsed_s=%s\n' "$(wc -l <"$file.counts.tsv")" "$restored" "$((SECONDS-began))"
cat "$file.counts.tsv"
