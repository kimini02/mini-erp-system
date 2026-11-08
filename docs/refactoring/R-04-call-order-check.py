#!/usr/bin/env python3
"""Read-only public entrypoint effect-order audit using attributed javac trees.

Receiver and arguments precede each call. Same-class calls/references are expanded
before comparison (including public helpers); recursion is listed for review.
Empty control-flow regions are omitted, but regions with effects retain if/else,
short-circuit, conditional, loop, try/catch/finally and lambda boundaries.
Only bean-field calls, clock reads and throws are effects. DTO/record operations,
streams, SqlStore value helpers and value-object methods are not effects.
--rename accepts Class#name or Class#name(parameter.types) for overloads.
"""
import argparse,importlib.util,json,re
from pathlib import Path
s=importlib.util.spec_from_file_location('analysis',Path(__file__).with_name('R-04-java-analysis.py'));a=importlib.util.module_from_spec(s);s.loader.exec_module(a)

def audit(units,renames=None):
 renames=renames or {};frames={};manual=[]
 def canonical(owner,sig):
  full=owner+'#'+sig;simple=owner+'#'+sig.split('(')[0]
  if full in renames:return renames[full]
  if simple in renames:return renames[simple]+sig[sig.index('('):]
  return full
 for unit,cls in a.classes(units):
  if not re.search(r'/(?:controller|service|handler|batch|scheduler)/',unit['file']):continue
  owner=cls['qualified'];methods={m['signature']:m for m in cls['members'] if m['kind']=='METHOD' and m['name']!='<init>'};fields={m['name']:m.get('type',m.get('declaredType')) for m in cls['members'] if m['kind']=='VARIABLE' and 'static' not in m['modifiers']}
  def sequence(nodes,stack):
   out=[]
   for n in nodes or []:out+=visit(n,stack)
   return out
  def region(kind,parts):
   return [{'region':kind,**parts}] if any(parts.values()) else []
  def expand(sig,stack):
   if sig not in methods:
    manual.append({'file':unit['file'],'owner':owner,'method':sig,'reason':'same-class resolution missing'});return []
   if sig in stack:
    manual.append({'file':unit['file'],'owner':owner,'method':sig,'reason':'recursive expansion stopped; inspect effect multiplicity'});return []
   return visit(methods[sig]['body'],stack+[sig])
  def field(n):
   if not n:return None
   if n['kind']=='IDENTIFIER' and n.get('symbolKind')=='FIELD' and n.get('name') in fields:return fields[n['name']]
   if n['kind']=='MEMBER_SELECT' and n.get('name') in fields and n.get('receiver',{}).get('name')=='this':return fields[n['name']]
   return None
  def visit(n,stack):
   if n is None:return []
   if isinstance(n,list):return sequence(n,stack)
   k=n['kind']
   if k=='METHOD_INVOCATION':
    select=n['select'];receiver=select.get('receiver') if select else None;out=visit(receiver,stack)+sequence(n['args'],stack)
    called=n.get('signature','');name=called.split('(')[0]
    if n.get('owner')==owner and called in methods:return out+expand(called,stack)
    if name=='now' and n.get('owner','').startswith('java.time.'):
     return out+[{'clock':n['owner']+'#'+name}]
    typ=field(receiver)
    if typ and typ!='java.time.Clock':return out+[{'bean':canonical(typ,called or select.get('name','<unresolved>'))}]
    return out
   if k=='MEMBER_REFERENCE':
    out=visit(n.get('receiver'),stack)
    if n.get('owner')==owner:return out+region('lambda',{'body':expand(n['signature'],stack)})
    typ=field(n.get('receiver'))
    if typ:return out+region('lambda',{'body':[{'bean':canonical(typ,n['signature'])}]})
    return out
   if k=='THROW':
    x=n['expression'];codes=[z['name'] for z in a.walk(x) if z.get('owner','').endswith('.ErrorCode') and z.get('symbolKind')=='ENUM_CONSTANT']
    return visit(x,stack)+[{'throw':x.get('type',x.get('class',k)),'errorCodes':codes}]
   if k=='IF':return region('if',{'condition':visit(n['condition'],stack),'then':visit(n['then'],stack),'else':visit(n['else'],stack)})
   if k=='CONDITIONAL_EXPRESSION':return region('conditional',{'condition':visit(n['condition'],stack),'then':visit(n['then'],stack),'else':visit(n['else'],stack)})
   if k in ('CONDITIONAL_AND','CONDITIONAL_OR'):return region(k,{'left':visit(n['left'],stack),'right':visit(n['right'],stack)})
   if k=='FOR_LOOP':return region('for',{'init':visit(n['init'],stack),'condition':visit(n['condition'],stack),'body':visit(n['body'],stack),'update':visit(n['update'],stack)})
   if k=='ENHANCED_FOR_LOOP':return region('foreach',{'iterable':visit(n['expression'],stack),'body':visit(n['body'],stack)})
   if k=='WHILE_LOOP':return region('while',{'condition':visit(n['condition'],stack),'body':visit(n['body'],stack)})
   if k=='DO_WHILE_LOOP':return region('doWhile',{'body':visit(n['body'],stack),'condition':visit(n['condition'],stack)})
   if k=='TRY':return region('try',{'resources':visit(n['resources'],stack),'body':visit(n['body'],stack),'catches':visit(n['catches'],stack),'finally':visit(n['finally'],stack)})
   if k=='CATCH':return region('catch:'+n['parameter']['declaredType'],{'body':visit(n['body'],stack)})
   if k=='LAMBDA_EXPRESSION':return region('lambda',{'body':visit(n['body'],stack)})
   if k in ('SWITCH','SWITCH_EXPRESSION'):return region('switch',{'selector':visit(n['expression'],stack),'cases':visit(n['cases'],stack)})
   if k=='CASE':return region('case',{'labels':visit(n['labels'],stack),'body':visit(n.get('body') or n.get('statements'),stack)})
   if k=='NEW_CLASS':return sequence(n['args'],stack)
   if k=='VARIABLE':return visit(n['initializer'],stack)
   if k in ('CLASS','RECORD','METHOD','ANNOTATION'):return []
   out=[]
   # Reader fields are emitted in Java evaluation order for non-branch expressions.
   for key,v in n.items():
    if key in ('parameters','annotations'):continue
    if isinstance(v,(dict,list)):out+=visit(v,stack)
   return out
  for sig,m in methods.items():
   if 'public' not in m['modifiers']:continue
   frames[canonical(owner,sig)]={'file':unit['file'],'line':m['line'],'events':expand(sig,[])}
 return {'frames':frames,'manualReview':list({json.dumps(x,sort_keys=True):x for x in manual}.values())}

def compare(before,after,renames):
 left=audit(before,renames);right=audit(after);diff=[]
 for key in sorted(left['frames'].keys()|right['frames'].keys()):
  x=left['frames'].get(key);y=right['frames'].get(key)
  if x is None or y is None or x['events']!=y['events']:diff.append({'method':key,'before':x,'after':y})
 return {'framesBefore':len(left['frames']),'framesAfter':len(right['frames']),'differences':len(diff),'differencesDetail':diff,'manualBefore':left['manualReview'],'manualAfter':right['manualReview'],'before':left['frames'],'after':right['frames']}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--classpath',type=Path,required=True);p.add_argument('--rename',action='append',default=[]);p.add_argument('--domain',action='append');p.add_argument('--cache',type=Path);p.add_argument('--output',type=Path,required=True);v=p.parse_args()
 def collect(root):
  files=[str(f.relative_to(root)) for f in (root/'backend/src/main/java').rglob('*.java') if (not v.domain or any('/domain/'+d+'/' in str(f) for d in v.domain)) and re.search(r'/(?:controller|service|handler|batch|scheduler)/',str(f))]
  return a.analyze(root,v.classpath,files,v.cache)
 result=compare(collect(v.before),collect(v.after),dict(x.split('=',1) for x in v.rename));a.output(result,v.output);raise SystemExit(bool(result['differences']))
if __name__=='__main__':main()
