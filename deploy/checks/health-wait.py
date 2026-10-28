#!/usr/bin/env python3
"""Fake systemctl/curl, real common.sh waiting logic, no service/network operations."""
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import time

DEPLOY = Path(__file__).resolve().parents[1]


def health_checks(before=False):
    source = (DEPLOY/'server/common.sh').read_text()
    if before:
        source = subprocess.check_output(['git','show','0271fef:deploy/server/common.sh'], cwd=DEPLOY.parent, text=True).replace('SECONDS+90','SECONDS+2')
    release = (DEPLOY/'server/release.sh').read_text()
    selection = "health_limit=90; migration=NO\n    if ((local_version>current_version)); then health_limit=900; migration=YES; backup_now; fi"
    assert selection in release
    assert 'health_wait "$health_limit" "$migration"' in release
    assert release.count('health_wait 90 NO') == 3, 'All rollback/recovery waits must remain 90 seconds'
    for scenario, version, expected, result_name in [
        ('LATE_UP', 7, 0, 'UP'), ('TIMEOUT', 6, 1, 'TIMEOUT'),
        ('RESTART', 7, 1, 'APP_FAILED'), ('FAILED', 7, 1, 'APP_FAILED'),
        ('STATUS_DELAY', 6, 1, 'TIMEOUT')
    ]:
        with tempfile.TemporaryDirectory(prefix='m5-health-') as folder:
            root = Path(folder)
            (root/'domain').write_text('m5-check.duckdns.org')
            (root/'common.sh').write_text(source.replace('/etc/workflow/domain', str(root/'domain')))
            # Only the test copy shortens 900->6 and 90->2. Selection is copied from release.sh.
            choose = selection.replace('health_limit=900', 'health_limit=6').replace('health_limit=90', 'health_limit=2')
            script = root/'run.sh'
            script.write_text('source '+shlex.quote(str(root/'common.sh'))+'\nbackup_now() { :; }\n'
                              +f'local_version={version}; current_version=6\n'+choose+'\nhealth_wait "$health_limit" "$migration"\n')
            (root/'systemctl').write_text('''#!/bin/sh
n=0; test ! -f "$FIXTURE/sys-count" || n=$(cat "$FIXTURE/sys-count")
n=$((n+1)); printf '%s' "$n" >"$FIXTURE/sys-count"
r=0; state=active
if test "$SCENARIO" = STATUS_DELAY && test "$n" -eq 2; then sleep 3; fi
if test "$SCENARIO" = RESTART && test "$n" -ge 3; then r=1; fi
if test "$SCENARIO" = FAILED && test "$n" -ge 3; then state=failed; fi
printf 'NRestarts=%s\\nActiveState=%s\\n' "$r" "$state"
''')
            (root/'curl').write_text('''#!/bin/sh
n=0; test ! -f "$FIXTURE/curl-count" || n=$(cat "$FIXTURE/curl-count")
n=$((n+1)); printf '%s' "$n" >"$FIXTURE/curl-count"
if test "$SCENARIO" = LATE_UP && test "$n" -ge 4; then printf '{"status":"UP"}'; exit 0; fi
printf '{"status":"DOWN"}'; exit 22
''')
            for name in ('systemctl','curl'): (root/name).chmod(0o700)
            env = dict(os.environ, PATH=folder+os.pathsep+os.environ['PATH'], FIXTURE=folder, SCENARIO=scenario)
            began = time.monotonic()
            run = subprocess.run(['bash',str(script)],env=env,capture_output=True,text=True,timeout=12)
            elapsed=time.monotonic()-began
            if before:
                assert run.returncode==1, (scenario,run.stdout,run.stderr)
                print(f'HEALTH_BEFORE scenario={scenario} exit={run.returncode} elapsed_s={elapsed:.3f} fixed_limit_s=2 systemd_checks=0')
                continue
            assert run.returncode==expected, (scenario,run.stdout,run.stderr)
            assert 'result='+result_name in run.stdout
            assert ('migration=NO' if version==6 else 'migration=YES') in run.stdout
            if scenario=='LATE_UP': assert elapsed>=3 and elapsed<6
            if scenario=='TIMEOUT': assert 1<=elapsed<4
            if scenario in ('RESTART','FAILED'): assert elapsed<3
            if scenario=='STATUS_DELAY': assert elapsed<5 and not (root/'curl-count').exists()
            print(f'HEALTH_FIXTURE scenario={scenario} exit={run.returncode} elapsed_s={elapsed:.3f} external_calls=0')
            print(run.stdout.strip())




def deadline_checks(before_source=None):
    """A successful response crosses the deadline while its body is processed.
    curl's 1.2s response is inside its 2s limit; fixture jq delays after reading it.
    This forces the boundary without relying on the wall clock's fractional second.
    """
    import shutil
    source = Path(before_source).read_text() if before_source else (DEPLOY/'server/common.sh').read_text()
    real_jq = shutil.which('jq')
    for scenario in ('UP', 'UP', 'UP', 'UP_THEN_RESTART'):
        with tempfile.TemporaryDirectory(prefix='m5-deadline-') as folder:
            root = Path(folder)
            (root/'domain').write_text('m5-check.duckdns.org')
            (root/'common.sh').write_text(source.replace('/etc/workflow/domain', str(root/'domain')))
            (root/'systemctl').write_text('''#!/bin/sh
r=0
if test "$SCENARIO" = UP_THEN_RESTART && test -f "$FIXTURE/up"; then r=1; fi
printf 'NRestarts=%s\\nActiveState=active\\n' "$r"
''')
            (root/'curl').write_text('''#!/bin/sh
sleep 1.2
touch "$FIXTURE/up"
printf '{"status":"UP"}'
''')
            (root/'jq').write_text('#!/bin/sh\nbody=$(cat)\nsleep 1\nprintf "%s" "$body" | '+shlex.quote(real_jq)+' "$@"\n')
            for name in ('systemctl','curl','jq'):
                (root/name).chmod(0o700)
            env = dict(os.environ, PATH=folder+os.pathsep+os.environ['PATH'], FIXTURE=folder, SCENARIO=scenario)
            began = time.monotonic()
            run = subprocess.run(['bash','-c','source "$1"; SECONDS=0; health_wait 2 NO','check',str(root/'common.sh')], env=env,capture_output=True,text=True,timeout=8)
            expected = 'TIMEOUT' if before_source else ('APP_FAILED' if scenario == 'UP_THEN_RESTART' else 'UP')
            assert run.returncode == (0 if expected == 'UP' else 1), (run.stdout,run.stderr)
            assert 'result='+expected in run.stdout, run.stdout
            print(f'DEADLINE_{"BEFORE" if before_source else "AFTER"} scenario={scenario} exit={run.returncode} elapsed_s={time.monotonic()-began:.3f} '+run.stdout.strip(), flush=True)


if __name__ == '__main__':
    health_checks()
    deadline_checks()
