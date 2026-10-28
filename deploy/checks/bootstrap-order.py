#!/usr/bin/env python3
"""Run in Ubuntu with flock. Fake commands execute only the bootstrap service tail."""
from pathlib import Path
import os
import shlex
import subprocess
import tempfile
import time

DEPLOY=Path(__file__).resolve().parents[1]
source=(DEPLOY/'server/bootstrap.sh').read_text()
assert source.index('lock_ops') < source.index('apt-get update')
tail=source[source.index('systemctl daemon-reload'):]
for scenario in ('DNS_OK','DNS_FAIL'):
    with tempfile.TemporaryDirectory(prefix='m5-bootstrap-') as folder:
        root=Path(folder)
        (root/'locks').mkdir()
        (root/'env').write_text('# test fixture, no secrets\n')
        (root/'common.sh').write_text((DEPLOY/'server/common.sh').read_text().replace('/run/lock',str(root/'locks')))
        for name,content in {
            'systemctl': '''#!/bin/bash
printf 'systemctl %s\\n' "$*" >>"$FIXTURE/actions"
if [[ $* == "restart mariadb redis-server nginx systemd-journald" ]]; then
  exec 7>"$FIXTURE/locks/workflow-maintenance.lock"
  if flock -n 7; then printf 'RESTART_WITHOUT_LOCK\\n' >>"$FIXTURE/actions"; exit 4; fi
  printf 'RESTART_LOCK_HELD\\n' >>"$FIXTURE/actions"
fi
if [[ $* == "restart duckdns-update.service" ]]; then
  exec 7>"$FIXTURE/locks/workflow-maintenance.lock"
  flock -n 7 || exit 5
  printf 'DNS_LOCK_RELEASED\\n' >>"$FIXTURE/actions"
  [[ $SCENARIO != DNS_FAIL ]] || exit 1
fi
''',
            'sshd': '''#!/bin/sh
printf 'sshd %s\\n' "$*" >>"$FIXTURE/actions"
if test "$1" = -T; then printf 'permitrootlogin no\\npasswordauthentication no\\n'; fi
''',
            'provision-db.sh': '#!/bin/sh\nprintf "PROVISION\\n" >>"$FIXTURE/actions"\n'
        }.items():
            (root/name).write_text(content);(root/name).chmod(0o700)
        runner=root/'run.sh'
        runner.write_text('source '+shlex.quote(str(root/'common.sh'))+'\nhere='+shlex.quote(folder)+'\nlock_ops\n'+tail.replace('/etc/workflow/env',str(root/'env')))
        env=dict(os.environ,PATH=folder+os.pathsep+os.environ['PATH'],FIXTURE=folder,SCENARIO=scenario)
        # Simulate a backup already holding the maintenance lock.
        guard=subprocess.Popen(['bash','-c','exec 9>"$1"; flock -x 9; touch "$2"; read -r release','guard',
                                str(root/'locks/workflow-maintenance.lock'),str(root/'ready')],stdin=subprocess.PIPE)
        for _ in range(100):
            if (root/'ready').exists():break
            time.sleep(.01)
        assert (root/'ready').exists()
        proc=subprocess.Popen(['bash',str(runner)],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        time.sleep(.3)
        assert not (root/'actions').exists(), 'Bootstrap bypassed existing backup lock'
        guard.communicate(b'release\n',timeout=5)
        out,err=proc.communicate(timeout=10)
        actions=(root/'actions').read_text()
        assert proc.returncode==(0 if scenario=='DNS_OK' else 1),(out,err,actions)
        assert 'RESTART_LOCK_HELD' in actions and 'DNS_LOCK_RELEASED' in actions
        assert actions.index('sshd -t') < actions.index('systemctl reload ssh') < actions.index('restart duckdns-update')
        if scenario=='DNS_FAIL':assert 'journalctl -u duckdns-update.service' in err and 'reset-failed' in err
        print(f'BOOTSTRAP_FIXTURE scenario={scenario} exit={proc.returncode} waited_for_backup=YES restart_locked=YES ssh_before_dns=YES dns_unlocked=YES')
