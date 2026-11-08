#!/usr/bin/env python3
"""Read-only exact javac string multiset audit, with the spec's Inbox movement exception.

Every file reports removed/added literals. Only InboxRepository's original response
construction region may lose literals; each removed occurrence must be newly present
with the identical value in InboxService. Only total/content/lines may be added to
that repository. SQL and parameter literals outside that region remain exact.
"""
import argparse,collections,importlib.util,json
from pathlib import Path
s=importlib.util.spec_from_file_location('sql',Path(__file__).with_name('R-03-sql-location-measure.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
ALLOWED={'PermissionAccess','BatchRunRecorder','InvitationTokenStore'}
INBOX='backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java'
SERVICE='backend/src/main/java/com/minierp/backend/domain/approval/service/InboxService.java'
def values(root,records=None):
 out={}
 for row in records if records is not None else m.scan(root):
  f=row['file']
  if row['kind']=='L' and ('/repository/' in f or '/global/persistence/' in f or '/global/' in f and Path(f).stem in ALLOWED):out.setdefault(f,collections.Counter())[row['value']]+=1
 return out

def inbox_exception(before,old_rows,new_rows,removed,added):
 source=(before/INBOX).read_text();start=source.find('        var content = documents.stream().map(')
 if start<0:return {'allowed':False,'reason':'Original response region not found; no broad fallback'}
 end=source.index('        return EmployeeService.page(content, page, size, total);',start)
 first=source.count('\n',0,start)+1;last=source.count('\n',0,end)+1
 movable=collections.Counter(r['value'] for r in old_rows if r['kind']=='L' and r['file']==INBOX and first<=r['line']<last)
 service_old=collections.Counter(r['value'] for r in old_rows if r['kind']=='L' and r['file']==SERVICE)
 service_new=collections.Counter(r['value'] for r in new_rows if r['kind']=='L' and r['file']==SERVICE)
 not_response=removed-movable;not_moved=removed-(service_new-service_old)
 invalid_added={k:v for k,v in added.items() if k not in {'total','content','lines'}}
 return {'allowed':not not_response and not not_moved and not invalid_added,'originalResponseLines':[first,last-1],
         'removedOutsideResponse':dict(not_response),'missingNewServiceOccurrences':dict(not_moved),
         'invalidAdded':invalid_added,'movedServiceOccurrences':dict((service_new-service_old)&removed)}

def compare(before,after):
 old_rows=m.scan(before);new_rows=m.scan(after);x=values(before,old_rows);y=values(after,new_rows);d=[];changes=[]
 for f in sorted(x.keys()|y.keys()):
  old=x.get(f,collections.Counter());new=y.get(f,collections.Counter());removed=old-new;added=new-old
  row={'file':f,'removed':dict(removed),'added':dict(added)}
  if removed or added:
   if f==INBOX:row['exception']=inbox_exception(before,old_rows,new_rows,removed,added)
   if not row.get('exception',{}).get('allowed'):d.append(row)
  changes.append(row)
 return {'filesBefore':len(x),'filesAfter':len(y),'occurrencesBefore':sum(sum(v.values()) for v in x.values()),'occurrencesAfter':sum(sum(v.values()) for v in y.values()),'differences':len(d),'differencesDetail':d,'fileChanges':changes}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=compare(a.before,a.after);a.output.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(json.dumps(r,ensure_ascii=False));raise SystemExit(bool(r['differences']))
if __name__=='__main__':main()
