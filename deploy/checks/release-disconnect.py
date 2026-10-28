#!/usr/bin/env python3
"""Destructive fixtures only inside the explicitly marked disposable systemd container.
No SSH, application, DB or external HTTP calls. Real PID 1/systemd-run; fake app health.
"""
import argparse
import fcntl
import os
from pathlib import Path
import shlex
import signal
import subprocess
import tarfile
import tempfile
import time

DEPLOY = Path(__file__).resolve().parents[1]
OLD = '20261027T120000Z-aaaaaaa'
NEW = '20261028T120000Z-bbbbbbb'


def wait_for(check, label, seconds=12):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if check():
            return
        time.sleep(.03)
    raise AssertionError('Timed out: '+label)


def fixture(root, source, scenario):
    for path in ('app/releases', 'web/releases', 'etc', 'locks', 'fake', 'scripts'):
        (root/path).mkdir(parents=True)
    for base in ('app', 'web'):
        release = root/base/'releases'/OLD
        release.mkdir()
        (release/('app.jar' if base == 'app' else 'index.html')).write_text('old')
        (root/base/'current').symlink_to(release)
    (root/'etc/domain').write_text('m5-check.duckdns.org')
    (root/'etc/env').write_text('FIXTURE_PASSWORD=CHANGE_ME_REDACTION_CHECK\n')
    (root/'etc/env').chmod(0o600)
    package = root/'package'
    (package/'dist').mkdir(parents=True)
    for name in ('app.jar', 'dist/index.html', 'dist/maintenance.html'):
        (package/name).write_text('fixture')
    archive = root/'upload.tar.gz'
    with tarfile.open(archive, 'w:gz') as out:
        out.add(package/'app.jar', arcname='app.jar')
        out.add(package/'dist', arcname='dist')
    mapping = {'/opt/workflow': str(root/'app'), '/var/www/workflow': str(root/'web'),
               '/etc/workflow': str(root/'etc'), '/run/lock/workflow': str(root/'locks/workflow'),
               '/var/lib/workflow-release': str(root/'jobs')}
    for name in ('common.sh', 'release.sh', 'release-job.sh'):
        path = source/name
        if not path.exists():
            continue
        text = path.read_text()
        for old, new in mapping.items():
            text = text.replace(old, new)
        if name == 'common.sh':
            text += '\nexport PATH='+shlex.quote(str(root/'fake'))+':$PATH\nexport FIXTURE='+shlex.quote(str(root))+'\n'
            text += 'export SCENARIO='+scenario+'\nschema_version() { printf "6\\n"; }\n'
        if name == 'release.sh':
            text = text.replace('health_limit=900', 'health_limit=8').replace('health_limit=90', 'health_limit=5').replace('health_wait 90 NO', 'health_wait 5 NO')
            text = text.replace('now=$(TZ=Asia/Seoul date +%H%M)', 'now=1200')
        (root/'scripts'/name).write_text(text)
        (root/'scripts'/name).chmod(0o700)
    (root/'fake/systemctl').write_text('''#!/bin/bash
if [[ $1 == show && $2 == workflow ]]; then
  state=active
  if [[ $SCENARIO == FAILURE && -e $FIXTURE/go && $(readlink "$FIXTURE/app/current") == */'''+NEW+''' ]]; then state=failed; fi
  printf 'NRestarts=0\\nActiveState=%s\\n' "$state"
elif [[ $2 == workflow && ( $1 == restart || $1 == stop ) ]]; then
  printf '%s\\n' "$1" >>"$FIXTURE/restarts"
else exec /usr/bin/systemctl "$@"; fi
''')
    (root/'fake/curl').write_text('''#!/bin/bash
if [[ ! -e $FIXTURE/ready ]]; then
  touch "$FIXTURE/ready"
  for ((i=0;i<400;i++)); do [[ -e $FIXTURE/go ]] && break; sleep .02; done
  [[ -e $FIXTURE/go ]] || exit 22
fi
printf '{"status":"UP"}'
''')
    (root/'fake/journalctl').write_text('#!/bin/sh\nprintf "Started BackendApplication in 1.0 seconds\\nCHANGE_ME_REDACTION_CHECK\\n"\n')
    for path in (root/'fake').iterdir():
        path.chmod(0o700)
    return archive


def state(root):
    previous = root/'etc/previous-release'
    return (Path(os.readlink(root/'app/current')).name,
            Path(os.readlink(root/'web/current')).name,
            previous.read_text().strip() if previous.exists() else 'MISSING')


def release_checks(before_source=None):
    assert os.geteuid() == 0 and Path('/run/m5-release-test-container').exists(), 'Use the marked disposable container only'
    assert Path('/proc/1/comm').read_text().strip() == 'systemd', 'Real systemd PID 1 required'
    source = Path(before_source) if before_source else DEPLOY/'server'
    for scenario in ('SUCCESS', 'FAILURE'):
        with tempfile.TemporaryDirectory(prefix='m5-disconnect-') as folder:
            root = Path(folder)
            archive = fixture(root, source, scenario)
            began = time.monotonic()
            if before_source:
                process = subprocess.Popen(['bash', str(root/'scripts/release.sh'), 'activate', NEW, str(archive), '6'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                wait_for(lambda: (root/'ready').exists(), 'old health wait')
                process.stdout.close()  # The SSH/client pipe reader disappears before next printf.
                (root/'go').touch()
                code = process.wait(timeout=10)
                assert code in (-signal.SIGPIPE, 128+signal.SIGPIPE), code
                assert state(root) == (NEW, OLD, 'MISSING'), state(root)
                assert (root/'restarts').read_text().splitlines() == ['restart']
                print(f'DISCONNECT_BEFORE scenario={scenario} exit={code} elapsed_s={time.monotonic()-began:.3f} links={state(root)} next_stage=NOT_RUN', flush=True)
                continue
            helper = str(root/'scripts/release-job.sh')
            held = (root/'locks/workflow-maintenance.lock').open('w')
            fcntl.flock(held, fcntl.LOCK_EX)
            start = subprocess.run(['bash', helper, 'start', 'activate', NEW, str(archive), '6'], capture_output=True, text=True, check=True)
            job = start.stdout.strip()
            directory = root/'jobs'/job
            wait_for(lambda: 'WAITING_FOR_MAINTENANCE' in (directory/'run.log').read_text(), 'maintenance queue')
            assert not (root/'ready').exists() and state(root) == (OLD, OLD, 'MISSING')
            queued = subprocess.run(['bash', helper, 'start', 'activate', NEW, str(archive), '6'], capture_output=True, text=True)
            assert queued.returncode == 75
            held.close()
            wait_for(lambda: (root/'ready').exists(), 'detached health wait')
            pid = int(subprocess.check_output(['/usr/bin/systemctl','show','workflow-release.service','-p','MainPID','--value'], text=True))
            parent = next(line for line in Path(f'/proc/{pid}/status').read_text().splitlines() if line.startswith('PPid:'))
            assert parent.split()[1] == '1', parent
            follower = subprocess.Popen(['bash', helper, 'follow', job], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            time.sleep(.1)
            follower.stdout.close()
            follower.send_signal(signal.SIGHUP)
            follower_code = follower.wait(timeout=3)
            # Also removes the SSH-owned upload; the detached worker must own its copy.
            archive.unlink()
            duplicate = subprocess.run(['bash', helper, 'start', 'rollback'], capture_output=True, text=True)
            assert duplicate.returncode == 75, (duplicate.returncode, duplicate.stdout, duplicate.stderr)
            busy = subprocess.run(['flock','-n',str(root/'locks/workflow-maintenance.lock'),'true'])
            assert busy.returncode == 1
            (root/'go').touch()
            wait_for(lambda: (directory/'exit.code').exists(), 'detached completion')
            code = int((directory/'exit.code').read_text())
            output = (directory/'run.log').read_text()
            expected = (NEW, NEW, OLD) if scenario == 'SUCCESS' else (OLD, OLD, 'MISSING')
            assert code == (0 if scenario == 'SUCCESS' else 1), output
            assert f'exit={code} interrupted=NO' in output
            assert state(root) == expected, (state(root), output)
            assert 'CHANGE_ME_REDACTION_CHECK' not in output
            assert ('result=UP' if scenario == 'SUCCESS' else 'ROLLBACK_OK') in output
            reconnect = subprocess.run(['bash', helper, 'follow', job], capture_output=True, text=True)
            assert reconnect.returncode == code and output in reconnect.stdout
            assert not (directory/'archive.tar.gz').exists()
            assert directory.stat().st_mode & 0o777 == 0o700
            for name in ('run.log','exit.code','request'):
                assert (directory/name).stat().st_mode & 0o777 == 0o600
            wait_for(lambda: subprocess.run(['flock','-n',str(root/'locks/workflow-maintenance.lock'),'true']).returncode == 0, 'lock released')
            print(f'DISCONNECT_AFTER scenario={scenario} follower_exit={follower_code} worker_exit={code} reconnect_exit={reconnect.returncode} duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s={time.monotonic()-began:.3f} links={state(root)}', flush=True)
            print(output.strip(), flush=True)
            # Dedicated rollback uses the same detached lifecycle, including loss of its reader.
            if scenario == 'SUCCESS':
                wait_for(lambda: subprocess.check_output(['/usr/bin/systemctl','show','workflow-release.service','-p','ActiveState','--value'], text=True).strip() in ('inactive','failed'), 'activation unit stopped')
                (root/'ready').unlink()
                (root/'go').unlink()
                start = subprocess.run(['bash', helper, 'start', 'rollback'], capture_output=True, text=True, check=True)
                job = start.stdout.strip()
                wait_for(lambda: (root/'ready').exists(), 'rollback health wait')
                follower = subprocess.Popen(['bash', helper, 'follow', job], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                follower.stdout.close()
                follower.send_signal(signal.SIGHUP)
                follower_code = follower.wait(timeout=3)
                (root/'go').touch()
                directory = root/'jobs'/job
                wait_for(lambda: (directory/'exit.code').exists(), 'rollback completion')
                output = (directory/'run.log').read_text()
                assert (directory/'exit.code').read_text().strip() == '0' and state(root) == (OLD,OLD,NEW), output
                assert 'ROLLBACK_OK' in output
                reconnect = subprocess.run(['bash', helper, 'follow', job], capture_output=True, text=True)
                assert reconnect.returncode == 0 and 'exit=0 interrupted=NO' in output
                print(f'DISCONNECT_AFTER scenario=MANUAL_ROLLBACK follower_exit={follower_code} worker_exit=0 reconnect_exit={reconnect.returncode} links={state(root)}', flush=True)
                print(output.strip(), flush=True)
            wait_for(lambda: subprocess.check_output(['/usr/bin/systemctl','show','workflow-release.service','-p','ActiveState','--value'], text=True).strip() in ('inactive','failed'), 'unit stopped')

    if not before_source:
        interruption_checks()


def interruption_checks(before_source=None):
    """Same two stop points against the original source first, then the corrected source."""
    assert os.geteuid() == 0 and Path('/run/m5-release-test-container').exists(), 'Use the marked disposable container only'
    assert Path('/proc/1/comm').read_text().strip() == 'systemd', 'Real systemd PID 1 required'
    source = Path(before_source) if before_source else DEPLOY/'server'
    # TERM is the systemctl stop/reboot path, repeated three times per stop point.
    # INT/HUP additionally verify the other explicit handlers after the fix.
    signals = ['TERM'] * 3 + ([] if before_source else ['INT', 'HUP'])
    for phase in ('WAITING', 'RUNNING'):
        for attempt, sig in enumerate(signals, 1):
            with tempfile.TemporaryDirectory(prefix='m5-interrupt-') as folder:
                root = Path(folder)
                archive = fixture(root, source, 'SUCCESS')
                helper = str(root/'scripts/release-job.sh')
                held = (root/'locks/workflow-maintenance.lock').open('w')
                if phase == 'WAITING':
                    fcntl.flock(held, fcntl.LOCK_EX)
                began = time.monotonic()
                try:
                    start = subprocess.run(['bash', helper, 'start', 'activate', NEW, str(archive), '6'], capture_output=True, text=True, check=True, timeout=10)
                    job = start.stdout.strip()
                    directory = root/'jobs'/job
                    wait_for(lambda: 'WAITING_FOR_MAINTENANCE' in (directory/'run.log').read_text(), 'worker waiting')
                    if phase == 'RUNNING':
                        wait_for(lambda: (root/'ready').exists(), 'health gate before interruption')
                    else:
                        assert not (root/'ready').exists()
                    expected_links = (OLD, OLD, 'MISSING') if phase == 'WAITING' else (NEW, OLD, 'MISSING')
                    assert state(root) == expected_links
                    if sig == 'TERM':
                        command = ['/usr/bin/systemctl', 'stop', 'workflow-release.service']
                    else:
                        command = ['/usr/bin/systemctl', 'kill', '--kill-who=all', '--signal='+sig, 'workflow-release.service']
                    stopped = subprocess.run(command, capture_output=True, text=True, timeout=12)
                    assert stopped.returncode == 0, stopped.stderr
                    wait_for(lambda: (directory/'exit.code').exists(), 'interrupted result recording')
                    wait_for(lambda: subprocess.check_output(['/usr/bin/systemctl', 'show', 'workflow-release.service', '-p', 'ActiveState', '--value'], text=True).strip() in ('inactive', 'failed'), 'interrupted unit stopped')
                    code = int((directory/'exit.code').read_text())
                    follow = subprocess.run(['bash', helper, 'follow', job], capture_output=True, text=True, timeout=5)
                    expected_code = 0 if before_source else 128 + getattr(signal, 'SIG'+sig)
                    assert code == expected_code and follow.returncode == expected_code, (code, follow.returncode, follow.stdout, follow.stderr)
                    output = (directory/'run.log').read_text()
                    assert state(root) == expected_links, (state(root), output)
                    assert 'ROLLBACK_OK' not in output and 'ACTIVATE release=' not in output
                    restarts = (root/'restarts').read_text().splitlines() if (root/'restarts').exists() else []
                    assert restarts == ([] if phase == 'WAITING' else ['restart']), restarts
                    if not before_source:
                        assert f'exit={expected_code} interrupted=YES' in output, output
                        assert 'INTERRUPTED:' in follow.stderr and '확인 후 재시도' in follow.stderr
                    # Releasing our test lock must not resume the cancelled work.
                    held.close()
                    assert subprocess.run(['flock', '-n', str(root/'locks/workflow-maintenance.lock'), 'true']).returncode == 0
                    assert not (root/'go').exists() and state(root) == expected_links
                    print(f'INTERRUPT_{"BEFORE" if before_source else "AFTER"} scenario={phase}_STOP signal={sig} attempt={attempt} stop_exit={stopped.returncode} exit.code={code} follow_exit={follow.returncode} links={state(root)} restart_calls={len(restarts)} elapsed_s={time.monotonic()-began:.3f}', flush=True)
                    print(next(line for line in output.splitlines() if line.startswith('RELEASE_RESULT ')), flush=True)
                    if follow.stderr.strip():
                        print(follow.stderr.strip(), flush=True)
                finally:
                    subprocess.run(['/usr/bin/systemctl', 'stop', 'workflow-release.service'], capture_output=True, timeout=12)
                    held.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--before-source', help='Original SSH disconnect baseline')
    modes.add_argument('--before-interrupt-source', help='Original interruption result baseline')
    modes.add_argument('--interrupt-only', action='store_true')
    args = parser.parse_args()
    if args.before_interrupt_source or args.interrupt_only:
        interruption_checks(args.before_interrupt_source)
    else:
        release_checks(args.before_source)
