#!/usr/bin/env python3
"""Regenerate component/type/original-expression evidence from compiled archives."""
import argparse, importlib.util, json, re, sys
from pathlib import Path
sys.dont_write_bytecode=True
parser=argparse.ArgumentParser(description=__doc__)
for name in ['before','after','classpath','mapping','output-dir']:
    parser.add_argument('--'+name,type=Path,required=True)
parser.add_argument('--cache',type=Path)
args=parser.parse_args()
T=args.output_dir.resolve();T.mkdir(parents=True,exist_ok=True)
BEFORE=args.before.resolve();AFTER=args.after.resolve();CP=args.classpath.resolve()
def mod(name):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(name+'.py'))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
def dump(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
a=mod('R-04-java-analysis');m=mod('R-04-response-check');base=a.analyze(BEFORE,CP,cache=args.cache);after=a.analyze(AFTER,CP,cache=args.cache);inv=m.inventory(base,True);classes=a.classes(base);method_index={c['qualified']+'#'+x['signature']:(u,x) for u,c in classes for x in c['members'] if x['kind']=='METHOD'}
records=m.records(after);mapping=json.loads(args.mapping.read_text());result=m.check(base,after,mapping);assert result['differences']==0
proof=[]
def expr(node,u):return (BEFORE/u['file']).read_text()[node['start']:node['end']]
def source(entry):
 if 'sql' in entry:
  owner,method=entry['sql'].split('#');u,body=next((u,x) for u,c in a.classes(base) if c['qualified']==owner for x in c['members'] if x['kind']=='METHOD' and x['name']==method)
  sql=next(n['value'] for n in a.walk(body['body']) if n['kind']=='STRING_LITERAL' and re.match(r'\s*SELECT\b',n['value'],re.I));return {f['key']:{'expression':'SELECT '+f['key']+' — '+sql,'file':u['file'],'line':body['line']} for f in m.sql_keys(base,entry['sql'])}
 row=next(x for x in inv['maps'] if x['method']==entry['method'] and x['index']==entry['index']);u,body=method_index[entry['method']];node=next(n for n in a.walk(body['body']) if n.get('start')==row['start'] and m.map_call(n))
 fields={k['value']:{'expression':expr(v,u),'file':u['file'],'line':v['line']} for k,v in zip(node['args'][::2],node['args'][1::2])}
 if 'putsFrom' in entry:
  origin=entry['putsFrom'];u,body=method_index[origin['method']]
  for n in a.walk(body['body']):
   if n['kind']=='METHOD_INVOCATION' and m.name(n)=='put' and n['select'].get('receiver',{}).get('name')==origin['receiver']:
    key=n['args'][0].get('value')
    if 'keys' not in origin or key in origin['keys']:fields[key]={'expression':expr(n['args'][1],u),'file':u['file'],'line':n['args'][1]['line']}
 return fields
for entry in mapping:
 original=source(entry)
 for field in records[entry['record']]:
  assert field['key'] in original,(entry,field)
  proof.append({'record':entry['record'],'key':field['key'],'component':field['component'],'type':field['type'],**original[field['key']]})
dump(T/'group3-response-types.json',proof)
lines=['# 묶음 3 응답 구성요소 대조','', '기준 d693843의 실제 map 인자·put 값·SELECT 별칭을 새 record 구성요소와 대조한다. 행의 정수는 `SqlStore.number`의 long, SUM은 BigDecimal, 날짜는 SqlStore가 변환한 LocalDate/LocalDateTime이다. 여러 출처가 같은 record를 만드는 경우 모두 남겼다. 원문 식의 SQL/행 키는 수정하지 않았다.','', '| record · 구성요소 | 타입 | 시작 코드의 실제 식/열 | 근거 위치 |','|---|---|---|---|']
for x in proof:
 typ=x['type'].replace('com.minierp.backend.domain.','').replace('java.lang.','').replace('java.util.','').replace('java.time.','');value=re.sub(r'\s+',' ',x['expression']).replace('|','\\|');name=x['record'].split('.dto.')[-1]
 lines.append(f"| `{name}.{x['component']}` | `{typ}` | `{value}` | `{x['file']}:{x['line']}` |")
(T/'group3-response-types.md').write_text('\n'.join(lines)+'\n');print('TYPE_COMPONENTS',len(proof),flush=True)
