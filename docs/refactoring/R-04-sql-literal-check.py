#!/usr/bin/env python3
"""Exact javac string-value multiset per SQL-owning source file; read-only."""
import argparse,collections,importlib.util,json
from pathlib import Path
s=importlib.util.spec_from_file_location('sql',Path(__file__).with_name('R-03-sql-location-measure.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
ALLOWED={'PermissionAccess','BatchRunRecorder','InvitationTokenStore'}
def values(root):
 out={}
 for row in m.scan(root):
  f=row['file']
  if row['kind']=='L' and ('/repository/' in f or '/global/persistence/' in f or '/global/' in f and Path(f).stem in ALLOWED):
   out.setdefault(f,collections.Counter())[row['value']]+=1
 return out
def compare(before,after):
 x=values(before);y=values(after);d=[]
 for f in sorted(x.keys()|y.keys()):
  old=x.get(f,collections.Counter());new=y.get(f,collections.Counter())
  if old!=new:d.append({'file':f,'removed':dict(old-new),'added':dict(new-old)})
 return {'filesBefore':len(x),'filesAfter':len(y),'occurrencesBefore':sum(sum(v.values()) for v in x.values()),'occurrencesAfter':sum(sum(v.values()) for v in y.values()),'differences':len(d),'differencesDetail':d}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=compare(a.before,a.after);a.output.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(json.dumps(r,ensure_ascii=False));raise SystemExit(bool(r['differences']))
if __name__=='__main__':main()
