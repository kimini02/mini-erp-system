#!/usr/bin/env python3
"""Exercise the embedded setup code in a temporary directory; no SMTP, root files or network.
Only OS ownership/TTY are adapted for a non-root fixture. All validation, quoting and atomic writes are real.
"""
from contextlib import redirect_stdout, redirect_stderr
from pathlib import Path
from unittest.mock import patch
import io
import os
import pty
import secrets
import stat
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[1] / 'server/setup-secrets.sh').read_text()
program = source.split("python3 - <<'PY'\n", 1)[1].rsplit('\nPY', 1)[0]
with tempfile.TemporaryDirectory(prefix='workflow-mail-secrets-') as folder:
    root = Path(folder)
    (root / 'domain').write_text('fixture.duckdns.org\n')
    code = program.replace('/etc/workflow', str(root))
    code = code.replace('.st_uid!=0', '.st_uid!=os.getuid()').replace('.st_gid!=0', '.st_gid!=os.getgid()')
    master, slave = pty.openpty()
    terminal = os.ttyname(slave)
    original_open = os.open
    password = secrets.token_hex(16) + '$`"\\'
    mail = ['smtp.example.test', '587', 'sender@example.test', password,
            'workflow@example.test', 'https://workflow.example.test/password/setup']

    def run(answers, should_fail=False):
        output = io.StringIO()
        values = iter(answers)
        calls = []

        def hidden(prompt):
            calls.append(prompt)
            return next(values)

        with patch('getpass.getpass', hidden), patch('os.chown'), patch('os.open',
                side_effect=lambda path, *args, **kwargs: original_open(terminal if path == '/dev/tty' else path,
                                                                       *args, **kwargs)), \
                redirect_stdout(output), redirect_stderr(output):
            try:
                exec(compile(code, 'setup-secrets-fixture', 'exec'), {})
            except SystemExit:
                assert should_fail
            else:
                assert not should_fail
        assert password not in output.getvalue()
        assert all('(hidden' in prompt for prompt in calls)
        return output.getvalue()

    try:
        admin_password = 'Fixture' + secrets.token_hex(4) + '!'
        run(['fixture-admin', 'Fixture', 'admin@example.test', admin_password, admin_password, '', *mail,
             'fixture', secrets.token_hex(16)])
        env = root / 'env'
        assert stat.S_IMODE(env.stat().st_mode) == 0o600
        assert stat.S_IMODE((root / 'duckdns').stat().st_mode) == 0o600
        # Bash consumes the exact EnvironmentFile subset used by deployment helpers. Values are never printed.
        script = 'source "$1"; [[ $APP_MAIL_ENABLED == true && $SMTP_PASSWORD == "$EXPECTED" ]]'
        result = subprocess.run(['bash', '-c', script, 'fixture', str(env)],
                                env={**os.environ, 'EXPECTED': password}, capture_output=True)
        assert result.returncode == 0 and not result.stdout and not result.stderr
        original = env.read_text()
        # Model an existing deployment after initial credentials have already been removed.
        env.write_text(''.join(line for line in original.splitlines(keepends=True)
                               if not line.startswith('INITIAL_ADMIN_')).replace('APP_MAIL_ENABLED="true"',
                                                                               'APP_MAIL_ENABLED="false"'))
        preserved = [line for line in env.read_text().splitlines() if line.startswith(('DB_', 'JWT_', 'JAVA_OPTS='))]
        run(mail)
        updated = env.read_text()
        assert all(line in updated for line in preserved)
        assert 'INITIAL_ADMIN_' not in updated
        assert updated.count('SMTP_PASSWORD=') == 1 and updated.count('APP_MAIL_ENABLED="true"') == 1
        run(mail)
        assert env.read_text() == updated
        run([''], should_fail=True)
        assert env.read_text() == updated
        run([*mail[:5], 'http://localhost:5173/password/setup'], should_fail=True)
        assert env.read_text() == updated
        print('MAIL_SECRETS first_setup=PASS existing_update=PASS rerun=IDENTICAL invalid_input=UNCHANGED')
        print('MAIL_SECRETS permissions=600 quoting=PASS hidden_input=PASS secret_output=0')
    finally:
        os.close(master)
        os.close(slave)
