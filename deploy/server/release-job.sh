#!/usr/bin/env bash
# SSH submits a root-owned job; systemd owns execution, while clients only follow files.
set +x
set -euo pipefail
here=$(dirname "$(readlink -f "$0")")
source "$here/common.sh"
require_root
umask 077
base=/var/lib/workflow-release
valid_job() { [[ $1 =~ ^(activate|rollback)-[0-9]{8}T[0-9]{6}Z-[A-Za-z0-9]{8}$ ]]; }
command=${1:-}; shift || true
case $command in
  start)
    action=${1:-}; shift || true
    case $action in
      activate)
        [[ $# == 3 ]] || exit 2
        release=$1; archive=$2; version=$3
        valid_release "$release"
        [[ $archive == /* && -f $archive && ! -L $archive && $version =~ ^[0-9]+$ ]] || exit 2
        ;;
      rollback) [[ $# == 0 ]] || exit 2;;
      *) exit 2;;
    esac
    mkdir -p /run/lock
    exec 7>/run/lock/workflow-release-submit.lock
    flock -n 7 || { printf '%s\n' 'RELEASE_BUSY: another submission is in progress.' >&2; exit 75; }
    state=$(systemctl show workflow-release.service --property=ActiveState --value)
    case $state in
      active|activating|deactivating|reloading)
        printf '%s\n' 'RELEASE_BUSY: follow the current server job; do not submit again.' >&2; exit 75;;
      inactive|failed) :;;
      *) printf '%s\n' 'Cannot determine release service state.' >&2; exit 1;;
    esac
    install -d -o root -g root -m 700 "$base"
    directory=$(mktemp -d "$base/$action-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")
    job=$(basename "$directory")
    : >"$directory/run.log"
    # Copy before acknowledgement: the upload belongs to the SSH client's cleanup trap.
    if [[ $action == activate ]]; then
      install -m 600 -- "$archive" "$directory/archive.tar.gz"
      printf '%s\n' activate "$release" "$directory/archive.tar.gz" "$version" >"$directory/request"
    else printf '%s\n' rollback >"$directory/request"; fi
    ln -s "$job" "$base/.latest.$$"
    mv -Tf "$base/.latest.$$" "$base/latest"
    # Fixed unit name rejects a second run, even after the submitting SSH process has died.
    # No --scope/--pipe/--pty/--wait: PID 1 owns the worker and all its output descriptors.
    if ! systemd-run --quiet --unit=workflow-release --collect --property=Type=exec \
        --property=UMask=0077 --property=StandardInput=null \
        --property="StandardOutput=append:$directory/run.log" \
        --property="StandardError=append:$directory/run.log" \
        "$here/release-job.sh" run "$job" 7>&-; then
      printf 'SUBMISSION_UNCONFIRMED job=%s; inspect the server result before retrying.\n' "$job" >&2
      exit 1
    fi
    printf '%s\n' "$job"
    ;;
  run)
    job=${1:?job required}; valid_job "$job" || exit 2
    directory="$base/$job"
    [[ -d $directory && ! -e $directory/exit.code ]] || exit 2
    # Only the fixed system service executes jobs, never a caller's interactive session.
    [[ -n ${INVOCATION_ID:-} ]] || { printf '%s\n' 'Jobs must run through systemd.' >&2; exit 2; }
    completed=NO
    release_result=125
    interrupted=NO
    interrupt_job() {
      interrupted=YES
      # Preserve the first caught signal while EXIT records its explicit status.
      trap '' TERM INT HUP
      exit "$1"
    }
    finish() {
      result=$?
      trap - EXIT
      trap '' TERM INT HUP
      if [[ $completed == YES && $interrupted == NO ]]; then
        # Keep the collected child status, including a failure just before EXIT.
        result=$release_result
      elif [[ $completed != YES && $result == 0 ]]; then
        # Unhandled terminating signals can run EXIT with a stale successful $?.
        result=125
        interrupted=UNKNOWN
      fi
      printf 'RELEASE_RESULT job=%s exit=%s interrupted=%s completed=%s\n' "$job" "$result" "$interrupted" "$completed"
      # Replace this shell: a kill-whom=all request can also hit newly forked rm/mv.
      # The same PID and inherited lock FD 9 survive through atomic result recording.
      exec python3 - "$directory" "$result" <<'PY_RESULT'
from pathlib import Path
import sys
folder=Path(sys.argv[1]); result=int(sys.argv[2])
try:
    (folder/'archive.tar.gz').unlink(missing_ok=True)
except OSError:
    print('RELEASE_CLEANUP_FAILED: archive retained; inspect the root-owned job directory.',file=sys.stderr)
temporary=folder/'.exit.code'
temporary.write_text(str(result)+'\n')
temporary.replace(folder/'exit.code')
raise SystemExit(result)
PY_RESULT
    }
    # Install signal handlers before EXIT: an interrupted wait can otherwise report $?=0.
    trap 'interrupt_job 143' TERM
    trap 'interrupt_job 130' INT
    trap 'interrupt_job 129' HUP
    trap finish EXIT
    printf 'RELEASE_JOB job=%s state=WAITING_FOR_MAINTENANCE\n' "$job"
    lock_ops
    printf 'RELEASE_JOB job=%s state=RUNNING\n' "$job"
    mapfile -t request <"$directory/request"
    # This is an external script: its own set -e remains in effect.
    if "$here/release.sh" "${request[@]}"; then
      release_result=0
    else
      release_result=$?
    fi
    completed=YES
    exit "$release_result"
    ;;
  follow)
    job=${1:?job or latest required}
    if [[ $job == latest ]]; then job=$(readlink "$base/latest"); fi
    valid_job "$job" || exit 2
    directory="$base/$job"
    [[ -d $directory ]] || exit 2
    python3 - "$directory" <<'PY'
from pathlib import Path
import signal,subprocess,sys,time
signal.signal(signal.SIGPIPE,signal.SIG_DFL)
folder=Path(sys.argv[1]); offset=0

def flush_log():
    global offset
    with (folder/'run.log').open('rb') as stream:
        stream.seek(offset)
        data=stream.read()
        offset=stream.tell()
    sys.stdout.buffer.write(data)
    sys.stdout.buffer.flush()

while True:
    flush_log()
    result=folder/'exit.code'
    if result.exists():
        flush_log()
        code=int(result.read_text())
        if code in (129,130,143):
            print('INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도',file=sys.stderr)
        elif code not in (0,1):
            print(f'ABNORMAL_EXIT: code={code}; inspect server logs and release links before retrying.',file=sys.stderr)
        raise SystemExit(code)
    state=subprocess.run(['systemctl','show','workflow-release.service','--property=ActiveState','--value'],capture_output=True,text=True,timeout=10)
    if state.returncode or state.stdout.strip() not in ('active','activating','deactivating','reloading'):
        if result.exists():
            continue
        print('OUTCOME_UNKNOWN: no final result; inspect service journal and both current links.',file=sys.stderr)
        raise SystemExit(125)
    time.sleep(.5)
PY
    ;;
  *) printf '%s\n' 'Usage: release-job.sh start activate RELEASE ARCHIVE MAX_VERSION | start rollback | follow JOB_OR_latest' >&2;exit 2;;
esac
