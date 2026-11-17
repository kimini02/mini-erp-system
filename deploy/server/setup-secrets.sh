#!/usr/bin/env bash
set +x
set -euo pipefail
[[ $EUID -eq 0 ]] || { printf '%s\n' 'Run with sudo.' >&2; exit 1; }
# All user data is read from /dev/tty with echo disabled. No secret CLI parameters.
python3 - <<'PY'
import getpass,os,tempfile,re,subprocess
try:
    tty_fd=os.open("/dev/tty",os.O_RDWR)
    try:
        if not os.isatty(tty_fd): raise OSError()
    finally:os.close(tty_fd)
except OSError: raise SystemExit("An interactive terminal is required; no secrets read.")
def random_secret(): return subprocess.check_output(["openssl","rand","-hex","32"],text=True).strip()
from pathlib import Path
path=Path('/etc/workflow/env');path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
if path.is_symlink(): raise SystemExit('Refusing symlink env file.')
values={}
if path.exists():
    if path.stat().st_uid!=0 or path.stat().st_mode & 0o777 != 0o600: raise SystemExit('Existing env must be root:root 600.')
    print('Existing DB/JWT secrets will be preserved. SMTP and invitation settings will be updated without displaying values.')
else:
    values.update(DB_APP_USERNAME='workflow_app',DB_APP_PASSWORD=random_secret(),DB_MIGRATOR_USERNAME='workflow_migrator',DB_MIGRATOR_PASSWORD=random_secret(),JWT_SECRET=random_secret(),JAVA_OPTS='-Duser.timezone=UTC -Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/lib/workflow/heap.hprof')
if not path.exists():
    for key,label in [('INITIAL_ADMIN_LOGIN_ID','Initial administrator login ID'),('INITIAL_ADMIN_NAME','Initial administrator name'),('INITIAL_ADMIN_EMAIL','Initial administrator email'),('INITIAL_ADMIN_PASSWORD','Initial administrator password')]:
        value=getpass.getpass(label+' (hidden): ')
        if not value or any(c in value for c in '\r\n\0'): raise SystemExit('Nonempty single-line values are required; nothing saved.')
        values[key]=value
    pw=values['INITIAL_ADMIN_PASSWORD']
    # Match PasswordPolicy.accepts: UTF-16 length, Java dot line terminators, and the exact symbol set.
    allowed_symbols='!@#$%^&*()_+-=[]{};\':"\\|,.<>/?'
    length=len(pw.encode('utf-16-le',errors='surrogatepass'))//2
    if not (8<=length<=20 and not any(c in pw for c in '\r\n\x85\u2028\u2029')
            and re.search('[A-Z]',pw) and re.search('[a-z]',pw) and re.search('[0-9]',pw)
            and any(c in allowed_symbols for c in pw)):
        raise SystemExit('Password must be 8-20 characters with upper/lowercase, number and symbol; nothing saved.')
    if getpass.getpass('Confirm initial password (hidden): ')!=pw: raise SystemExit('Passwords differ; nothing saved.')
    webhook=getpass.getpass('Optional Discord webhook URL (hidden; Enter to skip): ')
    if webhook:
        if not re.fullmatch(r'https://(?:discord.com|discordapp.com)/api/webhooks/[A-Za-z0-9_/-]+',webhook): raise SystemExit('Invalid webhook URL; nothing saved.')
        values['DISCORD_WEBHOOK_URL']=webhook
# Mail is required for new employees to set their first password in this deployment.
from urllib.parse import urlsplit
for key,label in [('SMTP_HOST','SMTP host (STARTTLS)'),('SMTP_PORT','SMTP port'),('SMTP_USERNAME','SMTP username'),('SMTP_PASSWORD','SMTP password'),('APP_MAIL_FROM','Sender email address'),('APP_INVITATION_SETUP_URL','Invitation URL (HTTPS domain + /password/setup)')]:
    value=getpass.getpass(label+' (hidden): ')
    if not value or value.isspace() or value=='CHANGE_ME' or any(c in value for c in '\r\n\0'):
        raise SystemExit('Nonempty single-line mail settings are required; nothing saved.')
    values[key]=value
if not values['SMTP_PORT'].isascii() or not values['SMTP_PORT'].isdigit() or not 1<=int(values['SMTP_PORT'])<=65535:
    raise SystemExit('Invalid SMTP port; nothing saved.')
try:
    url=urlsplit(values['APP_INVITATION_SETUP_URL'])
    host=(url.hostname or '').lower().rstrip('.')
    local=host=='localhost' or host.endswith('.localhost') or host.startswith('127.') or host in ('::1','0.0.0.0')
    if url.scheme!='https' or not host or local or url.username is not None: raise ValueError()
except ValueError: raise SystemExit('Invitation URL must use the public HTTPS domain; nothing saved.')
values['APP_MAIL_ENABLED']='true'
duck=Path('/etc/workflow/duckdns')
if duck.exists() or duck.is_symlink():
    if duck.is_symlink() or duck.stat().st_uid!=0 or duck.stat().st_gid!=0 or duck.stat().st_mode & 0o777!=0o600: raise SystemExit('Existing DuckDNS file must be root:root 600.')
    duck_token=duck.read_text().strip()
else:
    subdomain=getpass.getpass('DuckDNS subdomain without .duckdns.org (hidden): ')
    if not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?',subdomain): raise SystemExit('Invalid DuckDNS subdomain; nothing saved.')
    if Path('/etc/workflow/domain').read_text().strip()!=subdomain+'.duckdns.org':
        raise SystemExit('Subdomain differs from bootstrap domain; nothing saved. Check bootstrap configuration first.')
    duck_token=getpass.getpass('DuckDNS token (hidden): ')
if not re.fullmatch(r'[A-Za-z0-9-]+',duck_token):raise SystemExit('Invalid DuckDNS token; nothing saved.')
# A common quoted subset accepted by Bash and systemd EnvironmentFile.
def quote(value):
    for char in ['\\','"','$','`']: value=value.replace(char,'\\'+char)
    return '"'+value+'"'
# Preserve all unrelated settings byte-for-byte, including previously removed initial credentials.
existing=path.read_text() if path.exists() else ''
lines=[line for line in existing.splitlines(keepends=True)
       if line.split('=',1)[0] not in values]
fd,tmp=tempfile.mkstemp(dir=path.parent,prefix='.env.')
try:
    os.fchmod(fd,0o600)
    with os.fdopen(fd,'w') as f:
        retained=''.join(lines)
        f.write(retained)
        if retained and not retained.endswith('\n'): f.write('\n')
        for key,value in values.items(): f.write(key+'='+quote(value)+'\n')
    os.chown(tmp,0,0);os.replace(tmp,path)
finally:
    if os.path.exists(tmp):os.unlink(tmp)
if not duck.exists():
    fd,tmp=tempfile.mkstemp(dir=duck.parent,prefix='.duckdns.')
    try:
        os.fchmod(fd,0o600)
        with os.fdopen(fd,'w') as f:f.write(duck_token+'\n')
        os.chown(tmp,0,0);os.replace(tmp,duck)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)
print('Saved env and DuckDNS token (root:root 600). No secrets printed.')
PY
