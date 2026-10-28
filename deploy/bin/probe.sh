#!/usr/bin/env bash
set +x
set -euo pipefail
# The header file (0600, outside the repository) may hold a bearer token. Never pass it as an argument value.
python3 - "$@" <<'PY'
import argparse,concurrent.futures,csv,datetime,math,os,stat,subprocess,time,urllib.parse
p=argparse.ArgumentParser(description='Sample one GET API every 0.2 s; keep secrets in a 0600 header file.')
p.add_argument('url');p.add_argument('output');p.add_argument('--seconds',type=float,default=60);p.add_argument('--header-file')
a=p.parse_args();url=urllib.parse.urlsplit(a.url)
if url.scheme not in ('http','https') or not url.hostname or url.username or url.password or url.query or url.fragment:p.error('Use an HTTP(S) URL without credentials, query or fragment.')
if not math.isfinite(a.seconds) or not 0<a.seconds<=3600:p.error('seconds must be in (0,3600].')
if a.header_file and (not os.path.isfile(a.header_file) or stat.S_IMODE(os.stat(a.header_file).st_mode)!=0o600):p.error('header file must have mode 0600.')
if os.path.exists(a.output):p.error('output already exists; choose a new CSV file.')
command=['curl','--silent','--output',os.devnull,'--max-time','2','--connect-timeout','1','--write-out','%{http_code}\t%{time_total}']
if a.header_file:command+=['--header','@'+a.header_file]
command+=[a.url]
def sample(index):
 start=time.monotonic();stamp=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')
 try:
  result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,timeout=3)
  status,elapsed=result.stdout.strip().split('\t');status=int(status);elapsed=float(elapsed);code=result.returncode
 except (OSError,ValueError,subprocess.TimeoutExpired):status,elapsed,code=0,time.monotonic()-start,1
 return index,stamp,status,elapsed*1000,code,start,time.monotonic()
results=[];futures=[];begin=time.monotonic()
with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
 try:
  for index in range(math.ceil(a.seconds/.2)):
   deadline=begin+index*.2
   time.sleep(max(0,deadline-time.monotonic()))
   futures.append(pool.submit(sample,index))
 except KeyboardInterrupt:pass
 for future in concurrent.futures.as_completed(futures):results.append(future.result())
results.sort();failures=0;run=None;longest=0.0
with open(a.output,'x',newline='') as output:
 writer=csv.writer(output);writer.writerow(['timestamp_utc','status_code','elapsed_ms','curl_exit'])
 for _,stamp,status,elapsed,code,start,end in results:
  writer.writerow([stamp,f'{status:03d}',f'{elapsed:.3f}',code])
  failed=code!=0 or not 200<=status<300
  if failed:
   failures+=1
   if run is None:run=start
  elif run is not None:longest=max(longest,start-run);run=None
 if run is not None:longest=max(longest,max(r[6] for r in results)-run)
print(f'PROBE requests={len(results)} failures={failures} longest_failure_s={longest:.3f} interval_s=0.200')
PY
