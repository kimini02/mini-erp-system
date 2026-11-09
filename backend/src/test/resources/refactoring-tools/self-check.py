#!/usr/bin/env python3
"""CLI checks with synthetic contracts. No JUnit count changes, application/DB/network access."""
from pathlib import Path
import json, os, subprocess, sys, tempfile
classpath = Path(sys.argv[1] if len(sys.argv)>1 else 'target/refactoring-run-1/classpath.txt').read_text().strip()
java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
test = 'SelfCheck#contract()'
m5_boot = 'com.minierp.backend.integration.M5ProductionTest#<before-all>'
m5_serial = 'com.minierp.backend.integration.M5ProductionTest#separatedAccountsMigrateAndPerformRepresentativeWritesButRejectDdl()'
account_sql = ["create user 'workflow_app'@'%' identified by ?", "create user 'workflow_migrator'@'%' identified by ?"]
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
 ('strict-worker-sql-order', '{}','{}',1),
 ('candidate-worker-sql-order', '{}','{}',1),
 ('m5-create-users-only', '{}','{}',0),
 ('m5-other-sql-order', '{}','{}',1),
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
   worker=name.startswith('worker-') or name=='strict-worker-sql-order' or (name=='candidate-worker-sql-order' and side=='after')
   test_name=test
   if name in ('allowlisted-sql-order','m5-create-users-only','m5-other-sql-order'):
    test_name=m5_boot
    runs=[{'sql':sql,'count':1} for sql in ['select 1',*account_sql,'select database()']]
    if side=='after':
     a,b=(1,2) if name!='m5-other-sql-order' else (0,3)
     runs[a],runs[b]=runs[b],runs[a]
   elif 'multiplicity' in name:
    runs=[{'sql':'select database()','count':1 if side=='before' else 2},
          {'sql':'select 1','count':2 if side=='before' else 1}]
   elif 'sql-order' in name:
    runs=[{'sql':'select database()','count':1},{'sql':'select 1','count':1}]
    if side=='after':runs.reverse()
   count=sum(r['count'] for r in runs)
   if name=='strict-worker-sql-order':test_name=m5_serial
   (path/'sql.json').write_text(json.dumps({'distinctSql':sorted({r['sql'] for r in runs}),'tests':{test_name:{'queryCount':count,'comparedQueryCount':count,'diagnosticQueryCount':0,'workerSql':worker,'sql':runs}}}))
   (path/'responses.json').write_text(json.dumps({'tests':{test:[{'method':'GET','path':'/api/fixture','query':{},'status':400,'kind':'json','body':body}]}}))
  result=subprocess.run([java,'-cp',classpath,'com.minierp.backend.support.refactoring.SnapshotComparator',*map(str,paths)],capture_output=True,text=True)
  assert result.returncode==expected,(name,result.stdout,result.stderr)
  if expected: assert f'test={test_name} request=' in result.stderr and 'key=' in result.stderr
  if name=='candidate-worker-sql-order':
   assert 'key=workerSql ' in result.stderr and 'key=sql.order ' in result.stderr
  if name in ('strict-worker-sql-order','m5-other-sql-order'):assert 'key=sql.order ' in result.stderr
  if name in ('allowlisted-sql-order','m5-create-users-only'):
   assert 'REFACTORING_COMPARE differences=0 sqlOrderChanges=1' in result.stdout
  print(f'COMPARATOR_SELF_CHECK case={name} exit={result.returncode} expected={expected}')
print(f'COMPARATOR_SELF_CHECK passed={len(cases)}/{len(cases)}')
