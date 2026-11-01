#!/usr/bin/env python3
"""CLI checks with synthetic contracts. No JUnit count changes, application/DB/network access."""
from pathlib import Path
import json, os, subprocess, sys, tempfile
classpath = Path(sys.argv[1] if len(sys.argv)>1 else 'target/refactoring-run-1/classpath.txt').read_text().strip()
java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
test = 'SelfCheck#contract()'
cases = [
 ('object-key-order', '{"a":1,"b":null}', '{"b":null,"a":1}',0),
 ('number-10-vs-10.0', '{"amount":10}', '{"amount":10.0}',1),
 ('number-10.0-vs-10.00', '{"amount":10.0}', '{"amount":10.00}',1),
 ('null-key-removed', '{"value":null}', '{}',1),
 ('array-order', '{"items":[1,2]}', '{"items":[2,1]}',1),
 ('date-format', '{"date":"2026-04-06T09:00:00"}', '{"date":"2026-04-06T09:00:00Z"}',1),
 ('sql-alias', '{}','{}',1),
 ('query-count', '{}','{}',1),
 ('sql-multiplicity-same-total', '{}','{}',1),
 ('serial-sql-order', '{}','{}',1),
 ('worker-sql-order', '{}','{}',0),
 ('worker-sql-multiplicity', '{}','{}',1),
 ('allowlisted-sql-order', '{}','{}',0),
]
with tempfile.TemporaryDirectory(prefix='r01-self-check-') as folder:
 root=Path(folder)
 for name, before, after, expected in cases:
  paths=[]
  for side,body in [('before',before),('after',after)]:
   path=root/name/side;path.mkdir(parents=True);paths.append(path)
   sql='select 1 as changed' if name=='sql-alias' and side=='after' else 'select 1'
   count=2 if name=='query-count' and side=='after' else 1
   runs=[{'sql':sql,'count':count}]
   worker=name.startswith('worker-')
   if 'multiplicity' in name:
    runs=[{'sql':'select database()','count':1 if side=='before' else 2},
          {'sql':'select 1','count':2 if side=='before' else 1}]
   elif 'sql-order' in name:
    runs=[{'sql':'select database()','count':1},{'sql':'select 1','count':1}]
    if side=='after':runs.reverse()
   count=sum(r['count'] for r in runs)
   test_name='com.minierp.backend.integration.M5ProductionTest#<before-all>' if name=='allowlisted-sql-order' else test
   (path/'sql.json').write_text(json.dumps({'distinctSql':sorted({r['sql'] for r in runs}),'tests':{test_name:{'queryCount':count,'comparedQueryCount':count,'diagnosticQueryCount':0,'workerSql':worker,'sql':runs}}}))
   (path/'responses.json').write_text(json.dumps({'tests':{test:[{'method':'GET','path':'/api/fixture','query':{},'status':400,'kind':'json','body':body}]}}))
  result=subprocess.run([java,'-cp',classpath,'com.minierp.backend.support.refactoring.SnapshotComparator',*map(str,paths)],capture_output=True,text=True)
  assert result.returncode==expected,(name,result.stdout,result.stderr)
  if expected: assert 'test=SelfCheck#contract() request=' in result.stderr and 'key=' in result.stderr
  print(f'COMPARATOR_SELF_CHECK case={name} exit={result.returncode} expected={expected}')
print(f'COMPARATOR_SELF_CHECK passed={len(cases)}/{len(cases)}')
