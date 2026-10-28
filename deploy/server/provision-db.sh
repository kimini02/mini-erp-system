#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"
require_root
load_env
# Generated passwords are hex; SQL text travels through stdin, never argv or a log.
[[ $DB_APP_USERNAME == workflow_app && $DB_MIGRATOR_USERNAME == workflow_migrator ]]
[[ $DB_APP_PASSWORD =~ ^[0-9a-f]{64}$ && $DB_MIGRATOR_PASSWORD =~ ^[0-9a-f]{64}$ ]]
if ! python3 - <<'PY' | mariadb --protocol=socket --user=root --batch >/dev/null 2>/dev/null
import os
print("CREATE DATABASE IF NOT EXISTS mini_erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
for name,kind in [('workflow_migrator','MIGRATOR'),('workflow_app','APP')]:
    password=os.environ['DB_'+kind+'_PASSWORD']
    print(f"CREATE USER IF NOT EXISTS '{name}'@'localhost' IDENTIFIED BY '{password}';")
    print(f"ALTER USER '{name}'@'localhost' IDENTIFIED BY '{password}';")
    print(f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM '{name}'@'localhost';")
    grants='ALL PRIVILEGES' if kind=='MIGRATOR' else 'SELECT, INSERT, UPDATE, DELETE'
    print(f"GRANT {grants} ON mini_erp.* TO '{name}'@'localhost';")
PY
then printf '%s\n' 'DB account provisioning failed; secret-bearing SQL was not printed.' >&2; exit 1; fi
printf '%s\n' 'DB accounts ready: migrator=DDL+DML, app=SELECT/INSERT/UPDATE/DELETE, no GRANT OPTION.'
