#!/usr/bin/env bash
set +x
set -euo pipefail
[[ $EUID -eq 0 ]] || { printf '%s\n' 'Run with sudo.' >&2; exit 1; }
# URLs containing tokens exist only in Python memory, never in argv or exception logs.
python3 - <<'PY'
import ipaddress, re, subprocess, sys, time, urllib.parse, urllib.request
from pathlib import Path
TOKEN_PATH = Path('/etc/workflow/duckdns')
DOMAIN_PATH = Path('/etc/workflow/domain')
IMDS_BASE = 'http://169.254.169.254/latest'
UPDATE_URL = 'https://www.duckdns.org/update'
GETENT = '/usr/bin/getent'
BUDGET_SECONDS = 180
REQUEST_TIMEOUT = 5
INITIAL_DELAY = 2
MAX_DELAY = 20

def main():
    if TOKEN_PATH.is_symlink(): raise ValueError('unsafe settings')
    info = TOKEN_PATH.stat()
    if info.st_uid != 0 or info.st_gid != 0 or info.st_mode & 0o777 != 0o600:
        raise ValueError('unsafe settings')
    token = TOKEN_PATH.read_text().strip()
    domain = DOMAIN_PATH.read_text().strip()
    if not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.duckdns\.org', domain) or not re.fullmatch(r'[A-Za-z0-9-]+', token):
        raise ValueError('invalid settings')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    deadline = time.monotonic() + BUDGET_SECONDS
    delay, attempt, address, updated = INITIAL_DELAY, 0, None, False
    def timeout():
        remaining = deadline - time.monotonic()
        if remaining <= 0: raise TimeoutError()
        return min(REQUEST_TIMEOUT, remaining)
    def read(request, limit):
        with opener.open(request, timeout=timeout()) as response:
            return response.read(limit).decode().strip()
    while time.monotonic() < deadline:
        attempt += 1
        try:
            if not updated:
                request = urllib.request.Request(IMDS_BASE + '/api/token', method='PUT',
                    headers={'X-aws-ec2-metadata-token-ttl-seconds': '60'})
                session = read(request, 2048)
                request = urllib.request.Request(IMDS_BASE + '/meta-data/public-ipv4',
                    headers={'X-aws-ec2-metadata-token': session})
                address = str(ipaddress.IPv4Address(read(request, 64)))
                query = urllib.parse.urlencode({'domains': domain.removesuffix('.duckdns.org'), 'token': token, 'ip': address})
                if read(UPDATE_URL + '?' + query, 16) != 'OK': raise ValueError('update rejected')
                updated = True
                print(f'DUCKDNS update=OK public_ip={address}', flush=True)
            result = subprocess.run([GETENT, 'ahostsv4', domain], capture_output=True, text=True, timeout=timeout())
            resolved = sorted({str(ipaddress.IPv4Address(line.split()[0])) for line in result.stdout.splitlines() if line.strip()})
            matches = result.returncode == 0 and resolved == [address]
            print(f'DUCKDNS dns={"MATCH" if matches else "PENDING"} public_ip={address} resolved={",".join(resolved) or "NONE"}', flush=True)
            if matches: return
        except Exception:
            # HTTP exceptions may contain the entire secret URL; never print exception text.
            print(f'DUCKDNS attempt={attempt} stage={"DNS" if updated else "UPDATE"} retry=needed', flush=True)
        remaining = deadline - time.monotonic()
        if remaining <= 0: break
        pause = min(delay, remaining)
        print(f'DUCKDNS retry_delay_s={pause:.1f}', flush=True)
        time.sleep(pause)
        delay = min(delay * 2, MAX_DELAY)
    raise TimeoutError('update or DNS deadline')

try:
    main()
except Exception:
    print('DUCKDNS result=FAILED; check protected settings, IMDSv2, network and DNS.', file=sys.stderr)
    raise SystemExit(1)
print('DUCKDNS result=SUCCESS')
PY
