#!/usr/bin/env python3
"""Reproduce group-2 tool mutations in external copies (stdlib/JDK only).

Use --before for a clean archive of c531e1f, already test-compiled; --classpath
points to an existing recording/classpath.txt. The source is read-only. This
script copies it, mutates only the copies and never invokes databases or tests.
"""
import argparse,importlib.util,json,os,shutil,subprocess,sys
sys.dont_write_bytecode=True
from pathlib import Path

def module(path):
 spec=importlib.util.spec_from_file_location(path.stem,path);value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',type=Path,required=True);parser.add_argument('--classpath',type=Path,required=True);parser.add_argument('--output-dir',type=Path,required=True);parser.add_argument('--candidate',type=Path);args=parser.parse_args();out=args.output_dir.resolve();out.mkdir(parents=True,exist_ok=False);source=args.before.resolve();tools=Path(__file__).parent
 order=module(tools/'R-04-call-order-check.py');sql=module(tools/'R-04-sql-literal-check.py')
 doc='backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java'
 def save(name,value):(out/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
 baseline=order.a.analyze(source,args.classpath,[doc],out/'cache');same=order.compare(baseline,baseline,{});assert same['differences']==0;save('self.json',same)
 experiments={
  'handler-submitted-order':('        store.updateSubmission(id, no, LocalDateTime.now(clock));\n        h.onSubmitted(model(d));','        h.onSubmitted(model(d));\n        store.updateSubmission(id, no, LocalDateTime.now(clock));'),
  'handler-body-order':('        var history = queries.findHistory(id)','        var movedBody = handler((String) d.get("form_code")).body(id);\n        var history = queries.findHistory(id)')}
 result={}
 for name,(old,new) in experiments.items():
  root=out/name;shutil.copytree(source,root,ignore=shutil.ignore_patterns('.git','__pycache__'));path=root/doc;text=path.read_text();assert old in text;text=text.replace(old,new)
  if name=='handler-body-order':text=text.replace('"body", handler((String) d.get("form_code")).body(id)','"body", movedBody')
  path.write_text(text)
  with (out/(name+'-compile.log')).open('w') as log:subprocess.run(['./mvnw','-q','-Duser.timezone=UTC','test-compile'],cwd=root/'backend',stdout=log,stderr=subprocess.STDOUT,check=True)
  candidate=order.a.analyze(root,args.classpath,[doc],out/'cache');comparison=order.compare(baseline,candidate,{});save(name+'.json',comparison);assert comparison['differences']>=1;result[name]={'differences':comparison['differences']}
 same=sql.compare(source,source);assert same['differences']==0;save('sql-self.json',same)
 root=out/'sql-mutation';shutil.copytree(source,root,ignore=shutil.ignore_patterns('.git','target','__pycache__'));path=root/sql.INBOX;text=path.read_text();assert 'SELECT COUNT(*)' in text;path.write_text(text.replace('SELECT COUNT(*)','SELECT COUNT(1)',1));comparison=sql.compare(source,root,args.classpath);save('sql-mutation.json',comparison);assert comparison['differences']>=1;result['sql-mutation']={'differences':comparison['differences']}
 if args.candidate:
  candidate=args.candidate.resolve();comparison=sql.compare(source,candidate,args.classpath);save('inbox-positive.json',comparison);assert comparison['differences']==0
  experiments=[
   ('inbox-sql-parameter',sql.INBOX,'"actor", actor','"actor_changed", actor'),
   ('inbox-response-key',sql.ITEM,'String docNo,','String documentNo,'),
   ('inbox-overlapping-title-key',sql.ITEM,'String title,','String caption,'),
   ('inbox-row-key-count',sql.SERVICE,'document.get("doc_no")','document.get("title")'),
   ('inbox-state-count',sql.SERVICE,'case "APPROVED" -> "DONE"','case "APPROVED" -> "CURRENT"')]
  for name,path,old,new in experiments:
   root=out/name;shutil.copytree(candidate,root,ignore=shutil.ignore_patterns('.git','test-classes','surefire-reports','__pycache__'));p=root/path;text=p.read_text();assert old in text;p.write_text(text.replace(old,new,1));comparison=sql.compare(source,root,args.classpath);save(name+'.json',comparison);assert comparison['differences']>=1;result[name]={'differences':comparison['differences']}
  root=out/'inbox-unclassified-before';shutil.copytree(source,root,ignore=shutil.ignore_patterns('.git','test-classes','surefire-reports','__pycache__'));p=root/sql.INBOX;text=p.read_text();old='        var content = documents.stream().map(d -> {';assert old in text;p.write_text(text.replace(old,old+'\n            String unexpected = "UNCLASSIFIED";',1));comparison=sql.compare(root,candidate,args.classpath);save('inbox-unclassified.json',comparison);assert comparison['differences']>=1;result['inbox-unclassified']={'differences':comparison['differences']}
  files=[sql.INBOX,sql.SERVICE];before_units=order.a.analyze(source,args.classpath,files,out/'cache');after_units=order.a.analyze(candidate,args.classpath,files,out/'cache');owner='com.minierp.backend.domain.approval.service.InboxService';renames={owner+'#list':owner+'#getInboxDocuments',owner+'#counts':owner+'#getInboxCounts'};comparison=order.compare(before_units,after_units,renames);assert comparison['differences']==0;save('inbox-order-positive.json',comparison)
  root=out/'inbox-guard-after-query';shutil.copytree(candidate,root,ignore=shutil.ignore_patterns('.git','test-classes','surefire-reports','__pycache__'));p=root/sql.SERVICE;text=p.read_text();guard='        if (page < 0 || size < 1 || size > 100) {\n            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);\n        }\n';query='        var result = repository.search(actor, box, form, query, page, size);';assert guard in text and query in text;p.write_text(text.replace(guard,'').replace(query,query+'\n'+guard));mutated=order.a.analyze(root,args.classpath,files,out/'cache');comparison=order.compare(before_units,mutated,renames);save('inbox-guard-after-query.json',comparison);assert comparison['differences']>=1;result['inbox-guard-after-query']={'differences':comparison['differences']}
 save('summary.json',result);print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':main()
