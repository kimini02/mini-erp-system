#!/usr/bin/env python3
"""Real loopback HTTP only. Replace fixed paths/endpoints/time limits in a test copy.
Production has no endpoint override. DNS uses a fixture getent executable, not public DNS.
"""
import contextlib
import os
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread

DEPLOY = Path(__file__).resolve().parents[1]


def duckdns_checks():
    source = (DEPLOY / 'server/duckdns-update.sh').read_text().split("<<'PY'\n", 1)[1].rsplit('\nPY', 1)[0]
    for scenario in ('OK', 'KO', 'NO_RESPONSE', 'DELAYED_DNS', 'DNS_MISMATCH', 'DELAYED_IMDS'):
        with tempfile.TemporaryDirectory(prefix='m5-duckdns-') as folder:
            root = Path(folder)
            token = secrets.token_hex(32)
            (root / 'token').write_text(token)
            (root / 'token').chmod(0o600)
            (root / 'domain').write_text('m5-check.duckdns.org')
            calls = {'update': 0, 'imds': 0, 'invalid': 0}

            class Handler(BaseHTTPRequestHandler):
                def log_message(self, *args): pass
                def reply(self, body):
                    with contextlib.suppress(BrokenPipeError, ConnectionResetError):
                        self.send_response(200)
                        self.end_headers()
                        self.wfile.write(body.encode())
                def do_PUT(self):
                    calls['imds'] += 1
                    if self.path != '/latest/api/token' or self.headers.get('X-aws-ec2-metadata-token-ttl-seconds') != '60':
                        calls['invalid'] += 1
                    if scenario == 'DELAYED_IMDS' and calls['imds'] == 1:
                        time.sleep(1.0)
                    self.reply('fixture-session')
                def do_GET(self):
                    if self.path == '/latest/meta-data/public-ipv4':
                        if self.headers.get('X-aws-ec2-metadata-token') != 'fixture-session': calls['invalid'] += 1
                        self.reply('192.0.2.10')
                    elif self.path.startswith('/update?'):
                        calls['update'] += 1
                        values = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
                        if values != {'domains': ['m5-check'], 'token': [token], 'ip': ['192.0.2.10']}: calls['invalid'] += 1
                        time.sleep(1.0 if scenario == 'NO_RESPONSE' else .03)
                        self.reply('KO' if scenario == 'KO' else 'OK')
                    else:
                        calls['invalid'] += 1
                        self.send_error(404)

            server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
            Thread(target=server.serve_forever, daemon=True).start()
            resolver = root / 'getent'
            resolver.write_text(f'''#!{sys.executable}
from pathlib import Path
p=Path({str(root / 'dns-count')!r})
n=int(p.read_text())+1 if p.exists() else 1
p.write_text(str(n))
wrong={scenario == 'DNS_MISMATCH'!r} or ({scenario == 'DELAYED_DNS'!r} and n<3)
print(('192.0.2.11' if wrong else '192.0.2.10')+' STREAM m5-check.duckdns.org')
''')
            resolver.chmod(0o700)
            replacements = {
                "TOKEN_PATH = Path('/etc/workflow/duckdns')": f"TOKEN_PATH = Path({str(root / 'token')!r})",
                "DOMAIN_PATH = Path('/etc/workflow/domain')": f"DOMAIN_PATH = Path({str(root / 'domain')!r})",
                "IMDS_BASE = 'http://169.254.169.254/latest'": f"IMDS_BASE = 'http://127.0.0.1:{server.server_port}/latest'",
                "UPDATE_URL = 'https://www.duckdns.org/update'": f"UPDATE_URL = 'http://127.0.0.1:{server.server_port}/update'",
                "GETENT = '/usr/bin/getent'": f"GETENT = {str(resolver)!r}",
                'BUDGET_SECONDS = 180': 'BUDGET_SECONDS = 3',
                'REQUEST_TIMEOUT = 5': 'REQUEST_TIMEOUT = 0.5',
                'INITIAL_DELAY = 2': 'INITIAL_DELAY = 0.1',
                'MAX_DELAY = 20': 'MAX_DELAY = 0.4',
                'info.st_uid != 0 or info.st_gid != 0': f'info.st_uid != {os.getuid()} or info.st_gid != {os.getgid()}',
            }
            code = source
            for old, new in replacements.items():
                assert code.count(old) == 1, 'Production source changed: review fixture replacements'
                code = code.replace(old, new)
            program = root / 'run.py'
            program.write_text(code)
            log = root / 'output'
            samples = 0
            start = time.monotonic()
            try:
                with log.open('w') as stream:
                    proc = subprocess.Popen([sys.executable, str(program)], stdout=stream, stderr=stream)
                    while proc.poll() is None:
                        ps = subprocess.run(['ps', '-p', str(proc.pid), '-o', 'command='], capture_output=True, text=True)
                        assert token not in ps.stdout + ps.stderr, 'Token exposed in process arguments'
                        samples += 1
                        if time.monotonic()-start > 8:
                            proc.kill()
                            raise AssertionError('Bounded retry did not terminate')
                        time.sleep(.01)
                    status = proc.wait()
                output = log.read_text()
                success = scenario in ('OK', 'DELAYED_DNS', 'DELAYED_IMDS')
                assert status == (0 if success else 1), f'{scenario}: unexpected exit {status}'
                assert token not in output and '/update?' not in output and 'fixture-session' not in output, 'Secret log exposure'
                assert calls['invalid'] == 0 and samples > 0 and calls['update'] > 0
                if success: assert 'dns=MATCH public_ip=192.0.2.10 resolved=192.0.2.10' in output
                if scenario == 'DELAYED_DNS': assert int((root/'dns-count').read_text()) == 3
                if scenario == 'DNS_MISMATCH': assert 'dns=PENDING' in output and 'result=FAILED' in output
                if scenario == 'KO': assert 'update=OK' not in output
                print(f'DUCKDNS_LOCAL scenario={scenario} exit={status} update_calls={calls["update"]} ps_samples={samples} token_leaks=0 external_calls=0 elapsed_s={time.monotonic()-start:.3f}')
            finally:
                server.shutdown()
                server.server_close()

if __name__ == '__main__':
    duckdns_checks()
