#!/usr/bin/env python3
"""Read-only response inventory, source-to-record shape check and baseline coverage.

Mapping entries identify an original method and map-call index, or a repository
SELECT method for rows formerly returned directly. `omit`/`append` describe the
original conditional remove/put operations, with their source rationale recorded
in the mapping. Nested inline maps are recursively compared with record fields;
indirect nested values use explicit `nested` origin mappings. `putsFrom.keys`
selects one documented conditional shape; repeated put updates keep key order.
`removesFrom` reads literal remove keys from the original AST. Branch maps and
computeIfAbsent maps use their own method/index, including lambda maps. Sources
returned by another bean use a method/index or SELECT mapping and a documented
call chain. Keys never come
from response snapshots, so absent-success and always-null paths remain audited.
"""
import argparse,collections,importlib.util,json,re
from pathlib import Path
s=importlib.util.spec_from_file_location('analysis',Path(__file__).with_name('R-04-java-analysis.py'));a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
def name(n):return (n.get('select') or {}).get('name','')
def map_call(n):return n.get('kind')=='METHOD_INVOCATION' and name(n) in ('map','of') and n.get('owner') in ('com.minierp.backend.global.persistence.SqlStore','java.util.Map')
def shape(n):
 pairs=[]
 for key,value in zip(n['args'][::2],n['args'][1::2]):
  pairs.append({'key':key.get('value'), 'type':value.get('type',''), 'nested':shape(value) if map_call(value) else None})
 return pairs

def inventory(units,include_repository=False):
 maps=[];puts=[];removes=[];variables=[];public_maps=[];wildcards=[];apis=[]
 for unit,cls in a.classes(units):
  if '/repository/' in unit['file'] and not include_repository:continue
  prefix=''
  for ann in cls.get('annotations',[]):
   if ann['name'].endswith('RequestMapping'):
    prefix=next(iter(re.findall(r'"([^"]*)"',ann.get('raw',''))),'')
  for method in cls['members']:
   if method['kind']!='METHOD':continue
   owner=cls['qualified']+'#'+method['signature'];index=0
   if 'public' in method['modifiers'] and re.search(r'\bMap\b',method['returns']):public_maps.append({'method':owner,'file':unit['file'],'line':method['line'],'returns':method['returns']})
   if 'ApiResponse<?>' in method['returns']:wildcards.append({'method':owner,'file':unit['file'],'line':method['line']})
   for ann in method['annotations']:
    match=re.fullmatch(r'(?:.*\.)?(Get|Post|Put|Patch|Delete)Mapping',ann['name'])
    if match:
     paths=re.findall(r'"([^"]*)"',ann.get('raw','')) or ['']
     for path in paths:apis.append({'method':match[1].upper(),'path':prefix+path,'owner':owner})
   for n in a.walk([method['parameters'],method['body']]):
    loc={'file':unit['file'],'line':n['line'],'method':owner}
    if map_call(n):maps.append({**loc,'index':index,'start':n['start'],'keys':shape(n)});index+=1
    if n['kind']=='METHOD_INVOCATION' and name(n)=='put':
     recv=(n.get('select') or {}).get('receiver') or {}
     if re.fullmatch(r'java.util.(?:\w*Map)<java.lang.String,java.lang.Object>',recv.get('type','')):
      puts.append({**loc,'receiver':recv.get('name',''),'key':n['args'][0].get('value'),'valueType':n['args'][1].get('type'),'nested':shape(n['args'][1]) if map_call(n['args'][1]) else None})
    if n['kind']=='METHOD_INVOCATION' and name(n)=='remove':
     recv=(n.get('select') or {}).get('receiver') or {}
     if re.fullmatch(r'java.util.(?:\w*Map)<java.lang.String,java.lang.Object>',recv.get('type','')):
      removes.append({**loc,'receiver':recv.get('name',''),'key':n['args'][0].get('value')})
    if n['kind']=='VARIABLE' and re.search(r'Map<java.lang.String,java.lang.Object>',n.get('type','')):variables.append({**loc,'name':n['name'],'type':n['type']})
 return {'maps':maps,'puts':puts,'removes':removes,'rowMaps':variables,'publicMapMethods':public_maps,'wildcards':wildcards,'apis':apis}

def sql_keys(units,method):
 cls,name_=method.split('#',1);matches=[]
 for unit,c in a.classes(units):
  if c['qualified']!=cls:continue
  for m in c['members']:
   if m['kind']=='METHOD' and (m['signature']==name_ or m['name']==name_):
    strings=[n['value'] for n in a.walk(m['body']) if n['kind']=='STRING_LITERAL' and re.match(r'\s*SELECT\b',n['value'],re.I)]
    matches+=strings
 if len(matches)!=1:raise ValueError('SELECT source not unique: '+method)
 text=matches[0];start=re.match(r'\s*SELECT\s+',text,re.I).end();parts=[];part='';depth=0;quote=None;i=start
 while i<len(text):
  ch=text[i]
  if quote:
   part+=ch
   if ch==quote and (i==0 or text[i-1]!='\\'):quote=None
  elif ch in "'\"`":quote=ch;part+=ch
  elif ch=='(':depth+=1;part+=ch
  elif ch==')':depth-=1;part+=ch
  elif depth==0 and re.match(r'\sFROM\b',text[i:],re.I):parts.append(part);break
  elif ch==',' and depth==0:parts.append(part);part=''
  else:part+=ch
  i+=1
 keys=[]
 for column in parts:
  alias=re.search(r'\bAS\s+(\w+)\s*$',column,re.I)
  key=alias.group(1) if alias else column.strip().split('.')[-1].strip('`')
  if not re.fullmatch(r'\w+',key):raise ValueError('Unresolved SELECT column '+column)
  keys.append({'key':key,'type':'Object (SQL column; see schema type table)','nested':None})
 return keys

def records(units):
 out={}
 for unit,c in a.classes(units):
  if c['kind']!='RECORD':continue
  fields=[]
  for v in c['members']:
   if v['kind']!='VARIABLE' or 'static' in v['modifiers']:continue
   key=v['name']
   for ann in v.get('annotations',[]):
    if ann['name'].endswith('JsonProperty'):key=re.findall(r'"([^"]*)"',ann.get('raw',''))[0]
   fields.append({'key':key,'component':v['name'],'type':v['type']})
  out[c['qualified']]=fields
 return out

def check(before,after,mapping):
 inv=inventory(before,include_repository=True);rec=records(after);diff=[];rows=[]
 def check_entry(entry,label):
  if 'constant' in entry:
   owner,field=entry['constant'].split('#',1)
   initializers=[v['initializer'] for _,c in a.classes(before) if c['qualified']==owner for v in c['members'] if v['kind']=='VARIABLE' and v['name']==field]
   if len(initializers)!=1:raise ValueError('Literal-key list not unique: '+entry['constant'])
   fields=[{'key':n['value'],'type':'COUNT alias from original literal key list','nested':None} for n in a.walk(initializers[0]) if n['kind']=='STRING_LITERAL']
  elif 'sql' in entry:fields=sql_keys(before,entry['sql'])
  else:
   matches=[m for m in inv['maps'] if m['method']==entry['method'] and m['index']==entry['index']]
   if len(matches)!=1:raise ValueError('Original map not unique: '+str(entry))
   fields=matches[0]['keys']
  fields=[f for f in fields if f['key'] not in entry.get('omit',[])]
  fields+= [{'key':k,'type':'put (source rationale in mapping)','nested':None} for k in entry.get('append',[])]
  if 'putsFrom' in entry:
   origin=entry['putsFrom']
   found=[p for p in inv['puts'] if p['method']==origin['method'] and p['receiver']==origin['receiver']]
   if not found:raise ValueError('No put operations found: '+str(origin))
   origin_owner,origin_method=origin['method'].split('#',1)
   initializers=[n['initializer'] for _,c in a.classes(before) if c['qualified']==origin_owner for method in c['members'] if method['kind']=='METHOD' and method['signature']==origin_method for n in a.walk(method['body']) if n['kind']=='VARIABLE' and n['name']==origin['receiver']]
   expected=entry.get('method',entry.get('sql',''))
   calls=[n.get('owner','')+'#'+n.get('signature','') for initializer in initializers for n in a.walk(initializer) if n['kind']=='METHOD_INVOCATION']
   inline_source=any(map_call(initializer) and initializer.get('start')==matches[0]['start'] for initializer in initializers) if 'method' in entry and 'index' in entry else False
   if not inline_source and not any(call==expected or call.startswith(expected+'(') for call in calls):raise ValueError('put receiver does not originate from mapped map/SELECT: '+str(origin))
   if 'keys' in origin:
    if not set(origin['keys'])<=set(p['key'] for p in found):raise ValueError('Selected put key not found: '+str(origin))
    found=[p for p in found if p['key'] in origin['keys']]
   for put in found:
    field={'key':put['key'],'type':put['valueType'],'nested':put.get('nested')}
    previous=next((i for i,f in enumerate(fields) if f['key']==field['key']),None)
    if previous is None:fields.append(field)
    else:fields[previous]=field
  if 'removesFrom' in entry:
   origin=entry['removesFrom'];removed=[p['key'] for p in inv['removes'] if p['method']==origin['method'] and p['receiver']==origin['receiver']]
   if not removed or any(key is None for key in removed):raise ValueError('Literal remove operations required: '+str(origin))
   if 'keys' in origin and origin['keys']!=removed:raise ValueError('Remove keys differ: '+str(origin))
   fields=[field for field in fields if field['key'] not in removed]
  target=rec.get(entry['record']);source_keys=[f['key'] for f in fields];target_keys=[f['key'] for f in target] if target else None
  rows.append({'origin':label,'record':entry['record'],'before':fields,'after':target,'reason':entry.get('reason','')})
  if source_keys!=target_keys:diff.append({'origin':label,'record':entry['record'],'expected':source_keys,'actual':target_keys})
  for f in fields:
   if f['nested'] and target:
    component=next((x for x in target if x['key']==f['key']),None);nested_record=rec.get(component['type']) if component else None
    if not nested_record or [x['key'] for x in f['nested']]!=[x['key'] for x in nested_record]:diff.append({'origin':label+'.'+str(f['key']),'reason':'inline map nested record differs','source':f['nested'],'target':nested_record})
  for key,nested in entry.get('nested',{}).items():
   component=next((x for x in target or [] if x['key']==key),None)
   if component is None or nested['record'] not in component['type']:diff.append({'origin':label+'.'+key,'reason':'nested component type differs','component':component})
   check_entry(nested,label+'.'+key)
 for i,e in enumerate(mapping):check_entry(e,e.get('method',e.get('sql',e.get('constant','')))+':'+str(e.get('index','row')))
 return {'mappings':len(rows),'differences':len(diff),'differencesDetail':diff,'rows':rows}

def coverage(apis,baseline):
 responses=[r for rows in baseline['tests'].values() for r in rows];out=[]
 for api in apis:
  pattern='^'+re.sub(r'\\\{[^}]+\\\}',r'[^/]+',re.escape(api['path']))+'$'
  success=[r for r in responses if r['method']==api['method'] and re.fullmatch(pattern,r['path']) and 200<=r['status']<300]
  values=collections.defaultdict(lambda:[0,0])
  def visit(x,path):
   values[path][int(x is not None)]+=1
   if isinstance(x,dict):
    for k,v in x.items():visit(v,path+'.'+k)
   elif isinstance(x,list):
    for v in x:visit(v,path+'[]')
  for r in success:
   if r['kind']=='json':visit(json.loads(r['body']),'$')
  out.append({**api,'successSnapshots':len(success),'alwaysNull':[p for p,(null,nonnull) in sorted(values.items()) if null and not nonnull]})
 return out

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--classpath',type=Path,required=True);p.add_argument('--mapping',type=Path);p.add_argument('--domain',action='append',required=True);p.add_argument('--baseline',type=Path);p.add_argument('--cache',type=Path);p.add_argument('--output',type=Path,required=True);v=p.parse_args()
 def collect(root):
  files=[str(p.relative_to(root)) for p in (root/'backend/src/main/java').rglob('*.java') if any('/domain/'+d+'/' in str(p) for d in v.domain) or p.name=='PageResponseDto.java']
  return a.analyze(root,v.classpath,files,v.cache)
 before=collect(v.before);after=collect(v.after);r=check(before,after,json.loads(v.mapping.read_text()) if v.mapping else [])
 r['original']=inventory(before);r['remaining']=inventory(after)
 if v.baseline:r['coverage']=coverage(r['original']['apis'],json.loads(v.baseline.read_text()))
 a.output(r,v.output);raise SystemExit(bool(r['differences']))
if __name__=='__main__':main()
