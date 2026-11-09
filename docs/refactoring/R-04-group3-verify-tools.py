#!/usr/bin/env python3
"""Verify group-3 tools using compiled external archives; no database access.

--before: compiled archive of group-3 BASE d693843.
--classpath: dependency classpath.txt from an existing recording.
--output-dir: new directory for disposable copies and JSON evidence.
Requires JAVA_HOME pointing to the same JDK 17 used to compile --before.
"""
import argparse, importlib.util, json, os, shutil, subprocess, sys, time
from pathlib import Path
sys.dont_write_bytecode = True
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--before', type=Path, required=True)
parser.add_argument('--classpath', type=Path, required=True)
parser.add_argument('--output-dir', type=Path, required=True)
args = parser.parse_args()
T = args.output_dir.resolve()
T.mkdir(parents=True, exist_ok=False)
CP = args.classpath.resolve()
SOURCE = args.before.resolve()
TOOLS = Path(__file__).parent

def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def tool(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

def compile(root, label):
    with (T / (label + '-compile.log')).open('w') as log:
        subprocess.run(['./mvnw', '-q', '-Duser.timezone=UTC', 'test-compile'],
                       cwd=root / 'backend', stdout=log, stderr=subprocess.STDOUT, check=True)

files = ['backend/src/main/java/com/minierp/backend/' + f for f in [
    'domain/attendance/repository/AttendanceStore.java',
    'domain/attendance/service/AttendanceService.java',
    'domain/attendance/service/AttendanceAdminService.java',
    'domain/project/service/BoardProjectService.java',
    'domain/project/repository/ProjectStateStore.java',
    'domain/task/service/BoardTaskService.java',
    'domain/home/service/HomeService.java',
    'global/batch/BatchRunRecorder.java', 'global/persistence/SqlStore.java',
    'domain/user/service/EmployeeService.java', 'global/response/PageResponseDto.java']]
base = tool('R-04-java-analysis').analyze(SOURCE, CP, files, T / 'ast-cache')
dump(T / 'source-ast.json', base)
names=tool('R-04-names').measure(base,SOURCE);dump(T/'start-names.json',names)
assert any(x['name']=='d' and x['file'].endswith('home/service/HomeService.java') for x in names['domains']['home']['oneLetter'])
assert 'global' in names['domains']
print('NAMES_HOME_DATE_COUNTED',names['domains']['global']['oneLetterCount'],flush=True)
F='backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java'
D='backend/src/main/java/com/minierp/backend/domain/attendance/dto/AttendanceScheduleResponseDto.java'
C=T/'tool-schedule-positive';shutil.copytree(SOURCE,C,ignore=shutil.ignore_patterns('target','__pycache__'))
p=C/F;s=p.read_text();start=s.index('        public Map<String, Object> view()');end=s.index('\n        }',start)+10
s=s[:start]+'        public Map<String, Object> view() { return new LinkedHashMap<>(); }'+s[end:];p.write_text(s)
p=C/D;p.write_text('''package com.minierp.backend.domain.attendance.dto;
import com.minierp.backend.domain.attendance.repository.AttendanceStore.Schedule;
import java.time.format.DateTimeFormatter;
public record AttendanceScheduleResponseDto(String startTime, String endTime, int lateGraceMin,
        String halfBoundary, String source, String departmentName) {
    public static AttendanceScheduleResponseDto from(Schedule schedule) {
        var format = DateTimeFormatter.ofPattern("HH:mm");
        return new AttendanceScheduleResponseDto(schedule.start().format(format), schedule.end().format(format),
                schedule.grace(), schedule.start().plusHours(5).format(format), schedule.source(), schedule.department());
    }
}
''')
compile(C,'tool-schedule-positive')
sql=tool('R-04-sql-literal-check');positive=sql.compare(SOURCE,C,CP);dump(T/'tool-schedule-positive.json',positive);assert positive['differences']==0
mutations={
 'sql':(F,lambda s:s.replace('SELECT id,status,hire_date','SELECT id, status,hire_date')),
 'response-key':(D,lambda s:s.replace('String startTime','String changedStartTime')),
 'time-format':(D,lambda s:s.replace('"HH:mm"','"HH:mm:ss"')),
}
results={}
for label,(file,change) in mutations.items():
 candidate=T/('tool-schedule-negative-'+label);shutil.copytree(C,candidate)
 p=candidate/file;p.write_text(change(p.read_text()))
 compile(candidate,'tool-schedule-negative-'+label)
 result=sql.compare(SOURCE,candidate,CP);dump(T/('tool-schedule-negative-'+label+'.json'),result);assert result['differences']>=1
 results[label]=result['differences'];print('REJECTED',label,result['differences'],flush=True)
dump(T/'tool-proof-summary.json',{'schedulePositive':0,'scheduleMutations':results,'homeDateVariableCounted':True,'globalOneLetterCount':names['domains']['global']['oneLetterCount']})

m=tool('R-04-response-check')
before=base;inv=m.inventory(before,True)
def method(short):
 found={x['method'] for x in inv['maps'] if short in x['method']};assert len(found)==1,(short,found);return next(iter(found))
P=method('BoardProjectService#view(');PD='com.minierp.backend.domain.project.service.BoardProjectService#detail(long)'
TC=method('BoardTaskService#card(');TD=method('BoardTaskService#detail(long,')
MM=method('AttendanceAdminService#monthly(');ME=method('AttendanceService#me(');HW=method('HomeService#teamWeek(')
SV=method('AttendanceStore.Schedule#view(');AD='com.minierp.backend.domain.attendance.service.AttendanceAdminService#detail(long,java.time.LocalDate)'
state=method('ProjectStateStore#recalculate(')
cases=[
 ('ProjectDetail',{'method':P,'index':0,'putsFrom':{'method':PD,'receiver':'result'}},'id key title content status priority startDate endDate leader memberCount taskCount doneCount overdueCount progressPct myRole members permissions'),
 ('TaskDetail',{'method':TC,'index':0,'putsFrom':{'method':TD,'receiver':'result'}},'id key title status priority endDate overdueDays assignees position version canMove projectId content canManage history'),
 ('MovedEvent',{'method':TD,'index':0},'action actor at'),
 ('OtherEvent',{'method':TD,'index':0,'putsFrom':{'method':TD,'receiver':'event','keys':['from','to','target']}},'action actor at from to target'),
 ('EmptyDay',{'method':ME,'index':0},'date dayType'),
 ('RecordedDay',{'method':ME,'index':1},'date dayType clockInAt clockOutAt inResult outResult absent fixDocument'),
 ('TaskWeekItem',{'method':HW,'index':0},'type task project'),
 ('LeaveWeekItem',{'method':HW,'index':3},'type user status documentId'),
 ('AccumulatingRow',{'method':MM,'index':1},'user cells totals'),
 ('Totals',{'method':MM,'index':3},'late early absent missing leaveDays'),
 ('DetailSchedule',{'method':SV,'index':0,'removesFrom':{'method':AD,'receiver':'schedule','keys':['source','departmentName']}},'startTime endTime lateGraceMin halfBoundary'),
 ('StateSummary',{'method':state,'index':2},'id status taskCount doneCount progressPct'),
 ('BatchRun',{'sql':'com.minierp.backend.global.batch.BatchRunRecorder#record'},'id jobName targetDate status processedCount skippedCount message startedAt finishedAt'),
]
# Expected top-level keys are hand-written from the contract. Inline nested shapes are independently checked below.
nested={
 'ProjectDetail':{'leader':'id name title'},
 'MovedEvent':{'actor':'id name'},
 'OtherEvent':{'actor':'id name','target':'id name'},
 'RecordedDay':{'fixDocument':'id docNo'},
 'TaskWeekItem':{'task':'id key title','project':'id key'},
 'LeaveWeekItem':{'user':'id name'},
 'AccumulatingRow':{'user':'id name title department','totals':'late early absent missing leaveDays'},
}
C=T/'tool-response-positive';shutil.copytree(SOURCE,C,ignore=shutil.ignore_patterns('target','__pycache__'))
F='backend/src/main/java/com/minierp/backend/domain/attendance/dto/ToolShapeResponseDto.java'
lines=['package com.minierp.backend.domain.attendance.dto;','public record ToolShapeResponseDto() {'];mapping=[]
for name,entry,keys in cases:
 parts=[]
 for key in keys.split():
  child=nested.get(name,{}).get(key)
  typ=name+key[0].upper()+key[1:] if child else 'Object';parts.append(typ+' '+key)
  if child:lines.append('    public record '+typ+'('+', '.join('Object '+k for k in child.split())+') {}')
 lines.append('    public record '+name+'('+', '.join(parts)+') {}')
 mapping.append({**entry,'record':'com.minierp.backend.domain.attendance.dto.ToolShapeResponseDto.'+name})
lines.append('}');(C/F).write_text('\n'.join(lines)+'\n');compile(C,'tool-response-positive')
after=m.a.analyze(C,CP,[F],T/'ast-cache');r=m.check(before,after,mapping);dump(T/'tool-response-positive.json',r);assert r['differences']==0,r['differencesDetail']
# Mutation proves the conditional-put path is checked, including a non-always-present key.
source=(C/F).read_text();(C/F).write_text(source.replace('OtherEventTarget target','OtherEventTarget changedTarget'))
compile(C,'tool-response-negative');changed=m.a.analyze(C,CP,[F],T/'ast-cache');bad=m.check(before,changed,mapping);assert bad['differences']>=1;dump(T/'tool-response-negative.json',bad)
dump(T/'tool-response-mapping.json',mapping)
# A put that updates totals does not create a new key; these same five keys are retained.
updates=[p for p in inv['puts'] if p['method']==MM and p['receiver']=='t'];assert [p['key'] for p in updates]==['leaveDays']
summary={'cases':len(cases),'differences':r['differences'],'negativeDifferences':bad['differences'],'totalsUpdates':updates,'manualCallChains':['AttendanceAdminService.rejudge -> AbsenceBatchService.run -> BatchRunRecorder.record','BoardTaskService.move -> ProjectStateStore.recalculate'],'reason':'The returned row origin is checked by method/SELECT mapping; intermediate bean-call order is checked separately.'}
dump(T/'tool-response-proof-summary.json',summary);print('RESPONSE_SHAPES_VERIFIED',summary['cases'],flush=True)

names = tool('R-04-names').measure(base, SOURCE)
assert any(row['name'] == 'b' and row['method'] == 'bool' for row in names['domains']['global']['oneLetter'])
assert sum(row['name'] == 't' and row['method'] == 'time' for row in names['domains']['attendance']['oneLetter']) == 2
dump(T / 'pattern-proof.json', {'booleanPattern': True, 'timePatterns': 2, 'dateLoop': True})

# A record's data components are not injected beans. Preserve throw detection
# while matching the deliberately removed EmployeeService.page pure converter.
import copy
order = tool('R-04-call-order-check')
converted = copy.deepcopy(base)
for unit, owner in order.a.classes(converted):
    if owner['qualified'] == 'com.minierp.backend.domain.user.service.EmployeeService':
        owner['members'] = [member for member in owner['members']
                            if not (member['kind'] == 'METHOD' and member['name'] == 'page')]
renames = {
    'com.minierp.backend.domain.user.service.EmployeeService#page(java.util.List<?>,int,int,long)':
        'com.minierp.backend.global.response.PageResponseDto#of(java.util.List<T>,int,int,long)'
}
positive = order.compare(base, converted, renames)
assert positive['differences'] == 0
dump(T / 'page-converter-positive.json', positive)
for unit, owner in order.a.classes(converted):
    if owner['qualified'] == 'com.minierp.backend.global.response.PageResponseDto':
        method = next(member for member in owner['members']
                      if member['kind'] == 'METHOD' and member['name'] == 'of')
        method['body']['statements'].insert(0, {
            'kind': 'THROW', 'line': 1,
            'expression': {'kind': 'NEW_CLASS', 'type': 'java.lang.IllegalStateException', 'args': []}
        })
negative = order.compare(base, converted, renames)
assert negative['differences'] >= 1
dump(T / 'page-converter-negative.json', negative)
