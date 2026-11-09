#!/usr/bin/env python3
"""Verify R-04 analyzers against temporary, never-applied mutation copies.

All project sources and compiled outputs are read-only inputs. Temporary source
copies are removed on exit. The actual call-order CLI must exit 1 for each fault.
"""
import argparse,importlib.util,json,os,shutil,subprocess,tempfile
from pathlib import Path
s=importlib.util.spec_from_file_location('analysis',Path(__file__).with_name('R-04-java-analysis.py'));a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--start',type=Path,required=True);p.add_argument('--original',type=Path,required=True);p.add_argument('--classpath',type=Path,required=True);p.add_argument('--cache',type=Path);p.add_argument('--output',type=Path,required=True);v=p.parse_args();results={}
 unused=a.load_module('R-04-unused-imports');order=a.load_module('R-04-call-order-check')
 original=a.analyze(v.original,v.classpath,cache=v.cache);start=a.analyze(v.start,v.classpath,cache=v.cache)
 results['unusedOriginal']=unused.inspect(original);results['unusedStart']=unused.inspect(start)
 assert results['unusedOriginal']['unusedCount']==13 and results['unusedStart']['unusedCount']==0
 same=order.compare(start,start,{});assert same['differences']==0;results['sameStart']={'frames':same['framesBefore'],'differences':0}
 org='backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java';user='backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java'
 mutations=[('sql-order','org',org,'var departments = queries.findDepartments();\n        var counts = queries.findDepartmentMemberCounts();','var counts = queries.findDepartmentMemberCounts();\n        var departments = queries.findDepartments();'),('condition-boundary','org',org,'if (userId != null) {\n            var user = store.lockHeadCandidate(userId);','var user = store.lockHeadCandidate(userId);\n        if (userId != null) {'),('error-code','org',org,'ErrorCode.HEAD_NOT_IN_DEPARTMENT','ErrorCode.USER_NOT_FOUND'),('clock-order','user',user,None,None)]
 results['mutations']=[]
 with tempfile.TemporaryDirectory(prefix='r04-proof-') as temp:
  for label,domain,file,old,new in mutations:
   root=Path(temp)/label;shutil.copytree(v.start/'backend/src/main/java',root/'backend/src/main/java');(root/'backend/target').mkdir(parents=True);os.symlink(v.start.resolve()/'backend/target/classes',root/'backend/target/classes')
   path=root/file;source=path.read_text()
   if label=='clock-order':
    begin=source.index('public RegistrationResult register(');end=source.index('\n    @Transactional',begin);body=source[begin:end];body=body.replace('        queries.findDepartment(r.departmentId());','        var earlyNow = LocalDateTime.now(clock);\n        queries.findDepartment(r.departmentId());').replace('r.hireDate(), LocalDateTime.now(clock)','r.hireDate(), earlyNow');changed=source[:begin]+body+source[end:]
   else:assert source.count(old)==1;changed=source.replace(old,new)
   assert source!=changed;path.write_text(changed);output=root/'result.json'
   cmd=['python3',str(Path(__file__).with_name('R-04-call-order-check.py')),'--before',str(v.start),'--after',str(root),'--classpath',str(v.classpath),'--domain',domain,'--output',str(output)]
   if v.cache:cmd+=['--cache',str(v.cache)]
   run=subprocess.run(cmd,capture_output=True,text=True);result=json.loads(output.read_text()) if output.exists() else {'error':run.stderr};assert run.returncode==1 and result['differences']>0,result
   row={'name':label,'exit':run.returncode,'differences':result['differences'],'changedMethods':[x['method'] for x in result['differencesDetail']],'file':file};results['mutations'].append(row);print(json.dumps(row),flush=True)
 v.output.write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n');print('R04_TOOLS_PROOF_PASS')
if __name__=='__main__':main()
