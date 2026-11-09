#!/usr/bin/env python3
"""Read-only exact package/import-only move and test-import-only comparison."""
import argparse,json,re
from pathlib import Path

def strip(source,package=False):
    pattern=r"^(?:import\s+"+(r"|package\s+" if package else "")+r").*;\s*$"
    return "".join(line for line in source.splitlines(keepends=True) if not re.match(pattern,line))

def check(before,after,moved):
    checks=[]
    for old,new in moved.items():
        a,b=before/old,after/new
        ok=a.is_file() and b.is_file() and not (after/old).exists() and strip(a.read_text(),True)==strip(b.read_text(),True)
        checks.append({'before':old,'after':new,'kind':'package/import only','equal':ok})
    tests=[]
    for p in sorted((before/'backend/src/test').rglob('*')):
        if not p.is_file():continue
        relative=p.relative_to(before);q=after/relative
        if not q.is_file() or p.read_bytes()!=q.read_bytes():
            ok=q.is_file() and p.suffix=='.java' and strip(p.read_text())==strip(q.read_text())
            tests.append({'file':str(relative),'equal':ok,'beforeImports':[s for s in p.read_text().splitlines() if s.startswith('import ')], 'afterImports':[s for s in q.read_text().splitlines() if s.startswith('import ')] if q.is_file() else []})
    for p in (after/'backend/src/test').rglob('*'):
        if p.is_file() and not (before/p.relative_to(after)).exists():tests.append({'file':str(p.relative_to(after)),'equal':False,'reason':'new test file'})
    return {'moved':checks,'changedTests':tests,'differences':sum(not c['equal'] for c in checks+tests)}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--moved',action='append',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=check(a.before,a.after,dict(x.split('=',1) for x in a.moved));a.output.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(json.dumps(r,ensure_ascii=False));raise SystemExit(bool(r['differences']))
if __name__=='__main__':main()
