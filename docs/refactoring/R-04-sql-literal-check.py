#!/usr/bin/env python3
"""Read-only exact javac string multiset audit, with the user-confirmed Inbox occurrence movement exception.

Every file reports removed/added literals. Inbox's original response literals are
classified by their attributed AST occurrence: map key, row lookup, or state value.
The 16 map keys must match the item's nested record components. Row keys and state
values must increase by the same counts across InboxService and the item record.
Outside that region all literals (including SQL/parameters) remain exact. Only
total/content/lines may be added to InboxRepository. AttendanceStore.Schedule.view
may lose exactly six response-key occurrences and one HH:mm format occurrence;
outside-view literal order/values stay exact. No unknown category is allowed.
"""
import argparse,collections,importlib.util,json,os
from pathlib import Path
s=importlib.util.spec_from_file_location('sql',Path(__file__).with_name('R-03-sql-location-measure.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
ALLOWED={'PermissionAccess','BatchRunRecorder','InvitationTokenStore'}
INBOX='backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java'
SERVICE='backend/src/main/java/com/minierp/backend/domain/approval/service/InboxService.java'

s=importlib.util.spec_from_file_location('ast',Path(__file__).with_name('R-04-java-analysis.py'));a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
s=importlib.util.spec_from_file_location('response',Path(__file__).with_name('R-04-response-check.py'));response=importlib.util.module_from_spec(s);s.loader.exec_module(response)
ITEM='backend/src/main/java/com/minierp/backend/domain/approval/dto/ApprovalInboxItemResponseDto.java'
STATES={'DRAFT','CURRENT','WITHDRAWN','DONE','APPROVED','PENDING','IN_PROGRESS','REJECTED','SKIPPED','TODO'}

def values(root,records=None):
 out={}
 for row in records if records is not None else m.scan(root):
  f=row['file']
  if row['kind']=='L' and ('/repository/' in f or '/global/persistence/' in f or '/global/' in f and Path(f).stem in ALLOWED):out.setdefault(f,collections.Counter())[row['value']]+=1
 return out

def inbox_exception(before,after,old_rows,new_rows,removed,added,classpath):
 source=(before/INBOX).read_text();start=source.find('        var content = documents.stream().map(')
 if start<0:return {'allowed':False,'reason':'Original response region not found; no broad fallback'}
 end=source.index('        return EmployeeService.page(content, page, size, total);',start)
 invalid_added={k:v for k,v in added.items() if k not in {'total','content','lines'}}
 if not classpath:return {'allowed':False,'reason':'--classpath is required to verify Inbox movement by attributed occurrence','invalidAdded':invalid_added}
 if not (after/ITEM).is_file():return {'allowed':False,'reason':'Expected Inbox item record missing'}
 old=a.analyze(before,classpath,[INBOX]);new=a.analyze(after,classpath,[ITEM]);nodes=list(a.walk(old));classified={};maps=[]
 for n in nodes:
  if n.get('kind')!='METHOD_INVOCATION':continue
  if response.map_call(n):
   maps.append(n)
   for key in n['args'][::2]:
    if key.get('kind')=='STRING_LITERAL':classified[key['start']]='responseKey'
  elif n.get('select',{}).get('name')=='get' and n.get('owner','').startswith('java.util.'):
   if n['args'] and n['args'][0].get('kind')=='STRING_LITERAL':classified[n['args'][0]['start']]='rowKey'
  elif n.get('owner')=='com.minierp.backend.global.persistence.SqlStore' and n.get('select',{}).get('name')=='number':
   if len(n['args'])==2 and n['args'][1].get('kind')=='STRING_LITERAL':classified[n['args'][1]['start']]='rowKey'
 literals=[n for n in nodes if n.get('kind')=='STRING_LITERAL' and start<=n['start']<end];occurrences=[]
 for n in literals:
  category=classified.get(n['start']) or ('stateValue' if n['value'] in STATES else 'UNKNOWN')
  occurrences.append({'line':n['line'],'start':n['start'],'end':n['end'],'value':n['value'],'category':category})
 movable=collections.Counter(n['value'] for n in literals)
 # Exact subtraction also prevents a parameter elsewhere with the same value from disappearing.
 protected_old=collections.Counter(r['value'] for r in old_rows if r['kind']=='L' and r['file']==INBOX);protected_old.subtract(movable)
 protected_new=collections.Counter(r['value'] for r in new_rows if r['kind']=='L' and r['file']==INBOX);protected_new.subtract(added)
 protected_diff={v:{'before':protected_old[v],'after':protected_new[v]} for v in protected_old.keys()|protected_new.keys() if protected_old[v]!=protected_new[v]}
 response_keys=[r for r in occurrences if r['category']=='responseKey']
 roots=[n for n in maps if start<=n['start']<end and 'docNo' in [x.get('value') for x in n['args'][::2]]]
 def nested_map(value):
  if response.map_call(value):return value
  if value.get('kind')=='IDENTIFIER':
   matches=[n['right'] for n in nodes if n.get('kind')=='ASSIGNMENT' and n.get('left',{}).get('name')==value['name'] and response.map_call(n.get('right',{})) and start<=n['start']<end]
   if len(matches)==1:return matches[0]
  return None
 def map_paths(node,prefix=''):
  paths=[]
  for key,value in zip(node['args'][::2],node['args'][1::2]):
   path=prefix+str(key.get('value'));paths.append(path);child=nested_map(value)
   if child:paths.extend(map_paths(child,path+'.'))
  return paths
 rec=response.records(new)
 def record_paths(owner,prefix=''):
  paths=[]
  for field in rec.get(owner,[]):
   path=prefix+field['key'];paths.append(path)
   if field['type'] in rec:paths.extend(record_paths(field['type'],path+'.'))
  return paths
 source_paths=map_paths(roots[0]) if len(roots)==1 else []
 target_paths=record_paths('com.minierp.backend.domain.approval.dto.ApprovalInboxItemResponseDto')
 expected=collections.Counter(r['value'] for r in occurrences if r['category'] in {'rowKey','stateValue'})
 old_target=collections.Counter(r['value'] for r in old_rows if r['kind']=='L' and r['file'] in {SERVICE,ITEM});new_target=collections.Counter(r['value'] for r in new_rows if r['kind']=='L' and r['file'] in {SERVICE,ITEM})
 delta=new_target.copy();delta.subtract(old_target)
 count_diff={v:{'expectedIncrease':count,'actualIncrease':delta[v]} for v,count in expected.items() if delta[v]!=count}
 unknown=[r for r in occurrences if r['category']=='UNKNOWN'];counts=collections.Counter(r['category'] for r in occurrences)
 allowed=not invalid_added and not protected_diff and removed==movable and not unknown and len(response_keys)==16 and len(source_paths)==16 and source_paths==target_paths and not count_diff
 return {'allowed':allowed,'classificationCounts':dict(counts),'classifiedTotal':len(occurrences),'removedTotal':sum(removed.values()),'occurrences':occurrences,'unknownOccurrences':unknown,'protectedLiteralDifferences':protected_diff,'removedMatchesClassified':removed==movable,'responsePathsBefore':source_paths,'responsePathsAfter':target_paths,'expectedMovedOccurrences':dict(expected),'actualNewOccurrences':{v:delta[v] for v in expected},'movedOccurrenceDifferences':count_diff,'invalidAdded':invalid_added}


ATTENDANCE='backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java'
SCHEDULE='backend/src/main/java/com/minierp/backend/domain/attendance/dto/AttendanceScheduleResponseDto.java'
def schedule_exception(before,after,old_rows,new_rows,removed,added,classpath):
 if not classpath or not (after/SCHEDULE).is_file():return {'allowed':False,'reason':'Schedule record and attributed classpath required'}
 old=a.analyze(before,classpath,[ATTENDANCE]);new=a.analyze(after,classpath,[ATTENDANCE,SCHEDULE])
 views=[method for _,cls in a.classes(old) if cls['qualified'].endswith('AttendanceStore.Schedule') for method in cls['members'] if method['kind']=='METHOD' and method['name']=='view']
 if len(views)!=1:return {'allowed':False,'reason':'Original Schedule.view not unique'}
 view=views[0];literals=[n for n in a.walk(view) if n['kind']=='STRING_LITERAL']
 maps=[n for n in a.walk(view) if response.map_call(n)]
 keys=[n['value'] for n in maps[0]['args'][::2]] if len(maps)==1 else []
 expected=['startTime','endTime','lateGraceMin','halfBoundary','source','departmentName']
 key_starts={n['start'] for n in maps[0]['args'][::2]} if len(maps)==1 else set()
 occurrences=[{'line':n['line'],'start':n['start'],'value':n['value'],'category':'responseKey' if n['start'] in key_starts else 'timeFormat' if n['value']=='HH:mm' else 'UNKNOWN'} for n in literals]
 moved=collections.Counter(n['value'] for n in literals)
 original_outside=[n['value'] for n in a.walk(old) if n['kind']=='STRING_LITERAL' and not view['start']<=n['start']<view['end']]
 candidate_store=next(unit for unit in new if unit['file']==ATTENDANCE)
 candidate_outside=[n['value'] for n in a.walk(candidate_store) if n['kind']=='STRING_LITERAL']
 fields=response.records(new).get('com.minierp.backend.domain.attendance.dto.AttendanceScheduleResponseDto',[])
 target_keys=[field['key'] for field in fields]
 format_before=sum(row['file']==SCHEDULE and row['kind']=='L' and row['value']=='HH:mm' for row in old_rows)
 format_after=sum(row['file']==SCHEDULE and row['kind']=='L' and row['value']=='HH:mm' for row in new_rows)
 unknown=[row for row in occurrences if row['category']=='UNKNOWN']
 allowed=not added and removed==moved and len(literals)==7 and keys==expected==target_keys and original_outside==candidate_outside and format_after-format_before==1 and not unknown
 return {'allowed':allowed,'occurrences':occurrences,'removedTotal':sum(removed.values()),'classifiedTotal':len(occurrences),'responsePathsBefore':keys,'responsePathsAfter':target_keys,'outsideLiteralSequenceSame':original_outside==candidate_outside,'formatOccurrencesBefore':format_before,'formatOccurrencesAfter':format_after,'unknownOccurrences':unknown,'added':dict(added)}

def compare(before,after,classpath=None):
 old_rows=m.scan(before);new_rows=m.scan(after);x=values(before,old_rows);y=values(after,new_rows);d=[];changes=[]
 for f in sorted(x.keys()|y.keys()):
  old=x.get(f,collections.Counter());new=y.get(f,collections.Counter());removed=old-new;added=new-old
  row={'file':f,'removed':dict(removed),'added':dict(added)}
  if removed or added:
   if f==INBOX:row['exception']=inbox_exception(before,after,old_rows,new_rows,removed,added,classpath)
   if f==ATTENDANCE:row['exception']=schedule_exception(before,after,old_rows,new_rows,removed,added,classpath)
   if not row.get('exception',{}).get('allowed'):d.append(row)
  changes.append(row)
 return {'filesBefore':len(x),'filesAfter':len(y),'occurrencesBefore':sum(sum(v.values()) for v in x.values()),'occurrencesAfter':sum(sum(v.values()) for v in y.values()),'differences':len(d),'differencesDetail':d,'fileChanges':changes}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--classpath',type=Path);v=p.parse_args();r=compare(v.before,v.after,v.classpath);v.output.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(json.dumps(r,ensure_ascii=False));raise SystemExit(bool(r['differences']))
if __name__=='__main__':main()
