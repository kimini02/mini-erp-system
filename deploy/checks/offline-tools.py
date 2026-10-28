#!/usr/bin/env python3
"""No EC2/DuckDNS/SSH calls: local HTTP fixtures; fixture curl pins DNS to loopback."""
import csv
import json
import os
import shutil
import runpy
import subprocess
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread

DEPLOY = Path(__file__).resolve().parents[1]


def domain_checks():
    for domain, expected in [('m5-check.duckdns.org', 0), ('127.0.0.1', 2), ('example.com', 2), ('x.duckdns.org.example.com', 2)]:
        result = subprocess.run(['bash', '-c', 'source "$1"; remote_init "$2"', 'check', str(DEPLOY/'bin/common.sh'), domain], capture_output=True, text=True)
        assert result.returncode == expected, 'Remote address validation failed'
    with tempfile.TemporaryDirectory() as folder:
        for host in ('127.0.0.1', 'example.com'):
            result = subprocess.run(['bash', str(DEPLOY/'bin/probe.sh'), 'http://'+host+'/healthz', folder+'/unused.csv'], capture_output=True, text=True)
            assert result.returncode == 2 and not Path(folder+'/unused.csv').exists()
    print('DOMAIN_CHECK remote=4/4 probe_rejections=2/2 network_calls=0')


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
            # Production still validates a DuckDNS hostname; only test curl resolves it locally.
            wrapper = Path(folder) / 'curl'
            wrapper.write_text('#!/bin/sh\nexec '+shutil.which('curl')+' --noproxy \"*\" --resolve m5-check.duckdns.org:'+str(server.server_port)+':127.0.0.1 \"$@\"\n')
            wrapper.chmod(0o700)
            env = dict(os.environ, PATH=folder+os.pathsep+os.environ['PATH'])
            result = subprocess.run([
                'bash', str(DEPLOY / 'bin/probe.sh'),
                f'http://m5-check.duckdns.org:{server.server_port}/api/fixture', str(output), '--seconds', '2'
            ], capture_output=True, text=True, check=True, env=env)
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
    runpy.run_path(str(DEPLOY / "checks/duckdns-local.py"))["duckdns_checks"]()
    domain_checks()
    probe_and_log_checks()
