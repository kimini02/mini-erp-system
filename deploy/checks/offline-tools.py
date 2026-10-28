#!/usr/bin/env python3
"""No EC2/DuckDNS/SSH calls: local HTTP fixture + fully mocked DuckDNS transport."""
import contextlib
import csv
import io
import json
import secrets
import subprocess
import tempfile
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from types import SimpleNamespace
from unittest.mock import patch

DEPLOY = Path(__file__).resolve().parents[1]


def duckdns_checks():
    source = (DEPLOY / 'server/duckdns-update.sh').read_text()
    code = source.split("<<'PY'\n", 1)[1].rsplit('\nPY', 1)[0]
    token = secrets.token_hex(16)
    for outcome in ['OK', 'KO', 'exception']:
        calls = []

        class Opener:
            def open(self, request, timeout):
                calls.append(request)
                if len(calls) == 1:
                    assert request.method == 'PUT', 'Expected IMDSv2 PUT'
                    return io.BytesIO(b'ephemeral-session')
                if len(calls) == 2:
                    assert request.full_url.endswith('/public-ipv4'), 'Expected public IPv4'
                    return io.BytesIO(b'192.0.2.10')
                assert request.startswith('https://www.duckdns.org/update?'), 'Expected HTTPS'
                params = urllib.parse.parse_qs(urllib.parse.urlsplit(request).query)
                assert params['token'] == [token] and params['ip'] == ['192.0.2.10'], 'Parameters differ'
                if outcome == 'exception':
                    raise RuntimeError(request)
                return io.BytesIO(outcome.encode())

        def read_file(path, *args, **kwargs):
            return token if str(path) == '/etc/workflow/duckdns' else 'm5-check.duckdns.org'

        output = io.StringIO()
        status = 0
        with patch('pathlib.Path.is_symlink', return_value=False), \
                patch('pathlib.Path.stat', return_value=SimpleNamespace(st_uid=0, st_mode=0o600)), \
                patch('pathlib.Path.read_text', read_file), \
                patch('urllib.request.build_opener', return_value=Opener()), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            try:
                exec(compile(code, 'duckdns-inline', 'exec'), {})
            except SystemExit as error:
                status = error.code
        assert status == (0 if outcome == 'OK' else 1), 'Unexpected exit status'
        assert token not in output.getvalue() and 'https://' not in output.getvalue(), 'Secret URL leaked'
        assert len(calls) == 3, 'Unexpected request order'
        print(f'DUCKDNS_MOCK outcome={outcome} calls=3 exit={status} token_or_url_leaks=0 external_calls=0')


def probe_and_log_checks():
    class Handler(BaseHTTPRequestHandler):
        count = 0

        def log_message(self, *args):
            pass

        def do_GET(self):
            Handler.count += 1
            self.send_response(503 if 4 <= Handler.count <= 6 else 200)
            self.end_headers()

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with tempfile.TemporaryDirectory(prefix='m5-probe-') as folder:
            output = Path(folder) / 'probe.csv'
            result = subprocess.run([
                'bash', str(DEPLOY / 'bin/probe.sh'),
                f'http://127.0.0.1:{server.server_port}/api/fixture', str(output), '--seconds', '2'
            ], capture_output=True, text=True, check=True)
            with output.open() as stream:
                rows = list(csv.DictReader(stream))
            assert len(rows) == 10 and sum(row['status_code'] == '503' for row in rows) == 3
            assert 'failures=3' in result.stdout
            print(result.stdout.strip())
            print('PROBE_CHECK csv_rows=10 expected_503=3 actual_503=3')
    finally:
        server.shutdown()
        server.server_close()
    fixture = ''.join(json.dumps({
        'uri': f'/api/v1/tasks/{n}', 'request_method': 'GET', 'request_time': elapsed
    }) + '\n' for n, elapsed in [(1, .01), (2, .03), (3, .02)]) + 'not-json\n'
    slow = subprocess.run(['bash', str(DEPLOY / 'server/slow-endpoints.sh')],
                          input=fixture, text=True, capture_output=True, check=True)
    assert 'GET\t/api/v1/tasks/{id}\t3\t20.000\t30.000\t30.000' in slow.stdout
    assert 'invalid_lines=1' in slow.stderr
    print(slow.stdout.strip())
    print(slow.stderr.strip())


if __name__ == '__main__':
    duckdns_checks()
    probe_and_log_checks()
