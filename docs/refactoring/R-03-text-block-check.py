#!/usr/bin/env python3
"""Read-only raw closing-line check for moved Java SQL text blocks.

Compare SQL-call argument tokens, stopping at the SQL call's own closing parenthesis;
service-side > 0/stream processing is deliberately outside that boundary. Changed
argument expressions are reported separately. Also checks constant text-block declarations.
"""
import argparse,importlib.util,json
from pathlib import Path
import sys
sys.dont_write_bytecode = True

def lexer(root):
 s=importlib.util.spec_from_file_location('format_measure',root/'docs/refactoring/R-02-format-measure.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def calls(source,m):
 ts=m.tokenize(source);out=[]
 for i,t in enumerate(ts[:-4]):
  if t[1] not in ('db','jdbc') or ts[i+1][1]!='.' or ts[i+2][1] not in ('one','rows','count','insert','update','queryForList') or ts[i+3][1]!='(':continue
  depth=1;j=i+4
  while depth:
   if ts[j][1]=='(':depth+=1
   elif ts[j][1]==')':depth-=1
   j+=1
  out.append((t[2],ts[j-1][3],ts[i+2][1]))
 return out

def collect(root,m):
 out=[]
 for p in sorted((root/'backend/src/main/java').rglob('*.java')):
  source=p.read_text();tokens=m.tokenize(source);cs=calls(source,m)
  for t in tokens:
   if t[0]!='text':continue
   enclosing=[c for c in cs if c[0]<=t[2]<t[3]<=c[1]]
   if enclosing:
    a,b,op=min(enclosing,key=lambda c:c[1]-c[0]);context=source[a:b]
    after=source[t[3]:b]
   else:
    # Text-block constants/static SQL builders: stop at the first statement semicolon.
    following=[x for x in tokens if x[2]>=t[3]];semi=next((x[2] for x in following if x[1]==';'),None)
    if semi is None:continue
    after=source[t[3]:semi+1];op='expression'
   line_end=source.find('\n',t[3]);line_end=len(source) if line_end<0 else line_end
   suffix=source[t[3]:min(line_end,t[3]+len(after))]
   out.append({'file':str(p.relative_to(root)),'line':source.count('\n',0,t[3]-3)+1,'raw':t[1], 'op':op,'suffixTokens':[x[1] for x in m.tokenize(after)],'closingTokens':[x[1] for x in m.tokenize(suffix)],'closingLine':source[source.rfind('\n',0,t[3]-3)+1:line_end]})
 return out

def check(before,after):
 m=lexer(after);old=collect(before,m);new=collect(after,m);changed=[];checked=[];different=[]
 for n in new:
  if '/repository/' not in n['file']:continue
  matches=[o for o in old if o['raw']==n['raw'] and o['op']==n['op']]
  same=[o for o in matches if o['suffixTokens']==n['suffixTokens']]
  info={'target':n['file']+':'+str(n['line']),'origins':[o['file']+':'+str(o['line']) for o in matches]}
  if not same:
   different.append({**info,'reason':'SQL call argument tokens changed or no original text block'});continue
  checked.append(info)
  if not any(o['closingTokens']==n['closingTokens'] for o in same):
   changed.append({**info,'originalClosingLines':[o['closingLine'] for o in same],'closingLine':n['closingLine']})
 return {'checked':len(checked),'differences':len(changed),'violations':changed,'changedArguments':different}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=check(a.before,a.after);a.output.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(json.dumps(r,ensure_ascii=False));raise SystemExit(bool(r['differences']))
if __name__=='__main__':main()
