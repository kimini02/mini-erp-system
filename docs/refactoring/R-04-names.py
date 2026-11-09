#!/usr/bin/env python3
"""Read-only attributed variable-name and top-20 method-length inventory.

Counts domain/global local/parameter/lambda names; excludes catch parameters and
integer counters in classic for-loop headers. Date/value loop variables are counted.
Fields and record components are not local names.
Enhanced-for elements remain counted (they are not numeric loop indices).
"""
import argparse,collections,importlib.util,json,re
from pathlib import Path
s=importlib.util.spec_from_file_location('analysis',Path(__file__).with_name('R-04-java-analysis.py'));a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
def measure(units,root):
 out={};lengths=[]
 for unit,cls in a.classes(units):
  match=re.search(r'/domain/([^/]+)/',unit['file'])
  if not match and '/global/' not in unit['file']:continue
  domain=match[1] if match else 'global';d=out.setdefault(domain,{'oneLetter':[],'twoLetters':[]})
  for method in cls['members']:
   if method['kind']!='METHOD' or method['name']=='<init>':continue
   def integer_counters(loop):
    updated=set()
    for node in a.walk(loop.get('update',[])):
     if node.get('kind') in ('PREFIX_INCREMENT','POSTFIX_INCREMENT','PREFIX_DECREMENT','POSTFIX_DECREMENT'):
      updated.update(x.get('name') for x in a.walk(node) if x.get('kind')=='IDENTIFIER')
     if node.get('kind') in ('PLUS_ASSIGNMENT','MINUS_ASSIGNMENT'):
      updated.update(x.get('name') for x in a.walk(node.get('left',{})) if x.get('kind')=='IDENTIFIER')
    return {v['name'] for v in loop.get('init',[]) if v.get('kind')=='VARIABLE' and v.get('type') in ('byte','short','int','long') and v['name'] in updated}
   def visit(n,excluded=False,counters=frozenset()):
    if isinstance(n,list):
     for x in n:visit(x,excluded,counters)
    elif isinstance(n,dict):
     if n.get('kind')=='VARIABLE' and not excluded and n['name'] not in counters:
      name=n['name'];row={'file':unit['file'],'line':n['line'],'method':method['name'],'name':name}
      if len(name)==1:d['oneLetter'].append(row)
      elif len(name)==2:d['twoLetters'].append(row)
     for key,x in n.items():
      if isinstance(x,(dict,list)):
       skip=integer_counters(n) if n.get('kind')=='FOR_LOOP' and key in ('init','update') else counters
       visit(x,excluded or (n.get('kind')=='CATCH' and key=='parameter'),skip)
   visit(method['parameters']);visit(method['body'])
   source=(root/unit['file']).read_text();length=source[method['start']:method['end']].count('\n')+1
   lengths.append({'file':unit['file'],'line':method['line'],'method':cls['qualified']+'#'+method['signature'],'lines':length})
 for d in out.values():d['oneLetterCount']=len(d['oneLetter']);d['twoLetterCount']=len(d['twoLetters'])
 return {'domains':out,'longest20':sorted(lengths,key=lambda x:(-x['lines'],x['method']))[:20],'methods':lengths}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,required=True);p.add_argument('--classpath',type=Path,required=True);p.add_argument('--cache',type=Path);p.add_argument('--output',type=Path,required=True);v=p.parse_args();r=measure(a.analyze(v.root,v.classpath,cache=v.cache),v.root);a.output(r,v.output)
if __name__=='__main__':main()
