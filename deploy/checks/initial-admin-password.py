#!/usr/bin/env python3
"""Run actual setup-secrets validation against shared Java policy fixtures. No network/root files."""
from contextlib import redirect_stdout, redirect_stderr
from pathlib import Path
from unittest.mock import patch
import io
import json
import os
import pty
import secrets
import tempfile

repo = Path(__file__).resolve().parents[2]
source = (repo / "deploy/server/setup-secrets.sh").read_text()
program = source.split("python3 - <<'PY'\n", 1)[1].rsplit("\nPY", 1)[0]
cases = json.loads((repo / "backend/src/test/resources/onboarding/password-policy-cases.json").read_text())
master, slave = pty.openpty()
terminal = os.ttyname(slave)
original_open = os.open
failures = []
try:
    for case in cases:
        with tempfile.TemporaryDirectory(prefix="workflow-initial-password-") as folder:
            root = Path(folder)
            (root / "domain").write_text("fixture.duckdns.org\n")
            code = program.replace("/etc/workflow", str(root))
            code = code.replace(".st_uid!=0", ".st_uid!=os.getuid()").replace(".st_gid!=0", ".st_gid!=os.getgid()")
            smtp_password = secrets.token_hex(16)
            answers = iter(["fixture-admin", "Fixture", "fixture@example.test", case["value"], case["value"], "",
                            "smtp.example.test", "587", "sender@example.test", smtp_password,
                            "workflow@example.test", "https://workflow.example.test/password/setup",
                            "fixture", secrets.token_hex(16)])
            output = io.StringIO()
            accepted = True
            with patch("getpass.getpass", side_effect=lambda prompt: next(answers)), patch("os.chown"), \
                    patch("os.open", side_effect=lambda path, *args, **kwargs:
                          original_open(terminal if path == "/dev/tty" else path,
                                        args[0] | os.O_NOCTTY if path == "/dev/tty" else args[0],
                                        *args[1:], **kwargs)), \
                    redirect_stdout(output), redirect_stderr(output):
                try:
                    exec(compile(code, "setup-secrets-fixture", "exec"), {})
                except SystemExit:
                    accepted = False
            assert case["value"] not in output.getvalue() and smtp_password not in output.getvalue()
            assert (root / "env").exists() == accepted
            if accepted != case["accepted"]:
                failures.append(case["label"])
                print(f"FAIL {case['label']}: expected={case['accepted']} actual={accepted}")
finally:
    os.close(master)
    os.close(slave)
print(f"INITIAL_ADMIN_POLICY cases={len(cases)} mismatches={len(failures)} secret_output=0")
raise SystemExit(1 if failures else 0)
