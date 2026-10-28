#!/usr/bin/env bash
set +x
set -euo pipefail
[[ $EUID -eq 0 ]] || { printf '%s\n' 'Run with sudo.' >&2;exit 1; }
# Python keeps both tokens out of command arguments and suppresses URL-bearing errors.
python3 - <<'PY'
import ipaddress,re,sys,urllib.parse,urllib.request
from pathlib import Path
try:
    path=Path('/etc/workflow/duckdns')
    if path.is_symlink() or path.stat().st_uid!=0 or path.stat().st_mode & 0o777!=0o600:
        raise ValueError('unsafe token file')
    token=path.read_text().strip()
    domain=Path('/etc/workflow/domain').read_text().strip()
    if not re.fullmatch(r'[a-z0-9-]+\.duckdns\.org',domain) or not re.fullmatch(r'[A-Za-z0-9-]+',token):
        raise ValueError('invalid settings')
    # IMDS must never go through an environment proxy. Use the same direct opener for HTTPS.
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    request=urllib.request.Request('http://169.254.169.254/latest/api/token',method='PUT',headers={'X-aws-ec2-metadata-token-ttl-seconds':'60'})
    with opener.open(request,timeout=5) as response: session=response.read(2048).decode().strip()
    request=urllib.request.Request('http://169.254.169.254/latest/meta-data/public-ipv4',headers={'X-aws-ec2-metadata-token':session})
    with opener.open(request,timeout=5) as response: address=str(ipaddress.IPv4Address(response.read(64).decode().strip()))
    query=urllib.parse.urlencode({'domains':domain.removesuffix('.duckdns.org'),'token':token,'ip':address})
    with opener.open('https://www.duckdns.org/update?'+query,timeout=10) as response: result=response.read(16).decode().strip()
    if result!='OK':raise ValueError('update rejected')
except Exception:
    print('DUCKDNS update=FAILED; check token file, domain, IMDSv2 and outbound access.',file=sys.stderr)
    raise SystemExit(1)
print('DUCKDNS update=OK')
PY
