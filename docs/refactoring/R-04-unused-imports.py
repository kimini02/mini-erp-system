#!/usr/bin/env python3
"""Attribute unqualified identifiers to resolve single/static/wildcard import usage.

No token/name guesses: each use comes from javac's resolved Element. Explicit
imports take precedence over matching wildcard imports. Fully qualified names
and imports themselves do not count as import use. Existing source warnings and
Lombok attribution diagnostics are included for review; compilation is separate.
"""
import argparse,importlib.util,json
from pathlib import Path
s=importlib.util.spec_from_file_location('analysis',Path(__file__).with_name('R-04-java-analysis.py'));a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
def inspect(units):
 unused=[]
 for unit in units:
  used=set();imports=unit['imports']
  for ident in unit['identifiers']:
   candidates=[]
   for imp in imports:
    name=imp['name'];wild=name.endswith('.*');owner=name[:-2] if wild else name.rsplit('.',1)[0];short=ident['name']
    if not wild and name.rsplit('.',1)[-1]!=short:continue
    if imp['static']:
     match=ident['static'] and ident['owner']==owner
    else:match=ident['kind'] in ('CLASS','INTERFACE','ENUM','ANNOTATION_TYPE','RECORD') and (ident['owner']==owner if wild else ident['qualified']==name)
    if match:candidates.append(imp)
   singles=[x for x in candidates if not x['name'].endswith('.*')]
   for imp in singles or candidates:used.add((imp['name'],imp['static']))
  unused.extend({'file':unit['file'],**imp} for imp in imports if (imp['name'],imp['static']) not in used)
 return {'unusedCount':len(unused),'unused':unused,'diagnostics':[{'file':u['file'],'messages':u['diagnostics']} for u in units if u['diagnostics']]}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,required=True);p.add_argument('--classpath',type=Path,required=True);p.add_argument('--cache',type=Path);p.add_argument('--output',type=Path,required=True);v=p.parse_args();r=inspect(a.analyze(v.root,v.classpath,cache=v.cache));a.output(r,v.output);raise SystemExit(bool(r['unusedCount']))
if __name__=='__main__':main()
