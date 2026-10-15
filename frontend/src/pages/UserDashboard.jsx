import React from 'react';
import { useNavigate } from 'react-router-dom';
import { FolderKanban, CheckCircle2, Clock, Palmtree, FilePlus2, ChevronRight, Circle } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { getDisplayName } from '@/lib/roles';
import { cn } from '@/lib/cn';
import { Badge, Button, Card, CardHeader, CardBody, PageHeader, Progress, StatCard } from '@/components/ui';

// ---- API 연동 전 표시용 데이터 ----
const WEEK_SCHEDULE = [
  { title: '대시보드 UI 개발', type: 'project' },
  { title: '스프린트 회의', type: 'etc' },
  { title: '테스트 코드 작성', type: 'project' },
  { title: '연차 (오후)', type: 'leave' },
  { title: '주간 리뷰', type: 'etc' },
];

const TODOS = [
  { title: '로그인 API 구현', project: '그룹웨어 개발', status: '완료', dDay: '완료' },
  { title: '사용자 대시보드 UI 개발', project: '그룹웨어 개발', status: '진행중', dDay: 'D-2' },
  { title: '연차 결재 로직 검토', project: '그룹웨어 개발', status: '대기', dDay: 'D-5' },
  { title: '단위 테스트 코드 작성', project: '그룹웨어 개발', status: '대기', dDay: 'D-7' },
];

const MY_PROJECTS = [
  { title: '사내 그룹웨어 개발', percent: 65, status: '진행중' },
  { title: 'ERP 시스템 고도화', percent: 30, status: '검토중' },
  { title: '모바일 앱 리뉴얼', percent: 10, status: '기획중' },
];

const TODO_TONE = { 완료: 'green', 진행중: 'blue', 대기: 'gray' };

const scheduleTone = {
  project: 'border-blue-500 bg-blue-50 text-blue-800',
  leave: 'border-emerald-500 bg-emerald-50 text-emerald-800',
  etc: 'border-gray-300 bg-gray-50 text-gray-700',
};

/** 이번 주 월~금 날짜 */
function getThisWeek() {
  const today = new Date();
  const monday = new Date(today);
  monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
  return Array.from({ length: 5 }, (_, i) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + i);
    return d;
  });
}

const WEEKDAYS = ['월', '화', '수', '목', '금'];
const md = (d) => `${d.getMonth() + 1}/${d.getDate()}`;

const UserDashboard = () => {
  const { user } = useAuthStore();
  const navigate = useNavigate();

  const week = getThisWeek();
  const todayKey = new Date().toDateString();
  const avgProgress = Math.round(MY_PROJECTS.reduce((sum, p) => sum + p.percent, 0) / MY_PROJECTS.length);

  return (
    <>
      <PageHeader
        title={`안녕하세요, ${getDisplayName(user)}님`}
        description="오늘의 업무와 이번 주 일정을 확인하세요."
        actions={
          <Button icon={FilePlus2} onClick={() => navigate('/leaves/new')}>
            연차/특근 신청
          </Button>
        }
      />

      {/* 요약 지표 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="참여 중 프로젝트" value="3" unit="개" icon={FolderKanban} hint="이번 분기 기준" />
        <StatCard label="완료한 업무" value="12" unit="건" icon={CheckCircle2} hint="누적 완료" />
        <StatCard label="진행 중 업무" value="5" unit="건" icon={Clock} hint="마감 임박 2건" tone="amber" />
        <StatCard label="잔여 연차" value="9.5" unit="일" icon={Palmtree} hint="총 15일 중" tone="blue" />
      </div>

      {/* 이번 주 일정 */}
      <Card className="mt-4">
        <CardHeader
          title="이번 주 일정"
          description={`${md(week[0])} ~ ${md(week[4])}`}
          action={
            <Button variant="ghost" size="sm" onClick={() => navigate('/calendar')}>
              캘린더 <ChevronRight size={14} />
            </Button>
          }
        />
        <div className="grid grid-cols-1 divide-y divide-gray-100 sm:grid-cols-5 sm:divide-x sm:divide-y-0">
          {week.map((day, i) => {
            const isToday = day.toDateString() === todayKey;
            const item = WEEK_SCHEDULE[i];
            return (
              <div key={day.toISOString()} className="p-4">
                <p className={cn('mb-2 text-[13px]', isToday ? 'font-semibold text-blue-600' : 'text-gray-500')}>
                  {md(day)} ({WEEKDAYS[i]}){isToday && ' · 오늘'}
                </p>
                {item && (
                  <div className={cn('truncate rounded border-l-[3px] px-2 py-1.5 text-[13px] font-medium', scheduleTone[item.type])}>
                    {item.title}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </Card>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        {/* 내 할 일 */}
        <Card>
          <CardHeader
            title="내 할 일"
            action={
              <Button variant="ghost" size="sm" onClick={() => navigate('/my-tasks')}>
                내 업무 <ChevronRight size={14} />
              </Button>
            }
          />
          <ul className="divide-y divide-gray-100">
            {TODOS.map((todo) => {
              const done = todo.status === '완료';
              return (
                <li key={todo.title} className="flex items-center gap-3 px-5 py-3">
                  {done ? (
                    <CheckCircle2 size={18} className="shrink-0 text-emerald-500" />
                  ) : (
                    <Circle size={18} className="shrink-0 text-gray-300" />
                  )}
                  <div className="min-w-0 flex-1">
                    <p className={cn('truncate text-sm', done ? 'text-gray-400 line-through' : 'font-medium text-gray-900')}>
                      {todo.title}
                    </p>
                    <p className="mt-0.5 text-xs text-gray-500">{todo.project}</p>
                  </div>
                  <Badge tone={TODO_TONE[todo.status]}>{todo.status}</Badge>
                  <span className={cn('w-10 text-right text-xs tabular-nums', done ? 'text-gray-400' : 'font-medium text-gray-700')}>
                    {done ? '-' : todo.dDay}
                  </span>
                </li>
              );
            })}
          </ul>
        </Card>

        {/* 참여 프로젝트 */}
        <Card>
          <CardHeader title="참여 프로젝트" description={`평균 진척률 ${avgProgress}%`} />
          <CardBody className="space-y-5">
            {MY_PROJECTS.map((p) => (
              <div key={p.title}>
                <div className="mb-2 flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-medium text-gray-900">{p.title}</span>
                  <span className="text-xs tabular-nums text-gray-500">{p.percent}%</span>
                </div>
                <Progress value={p.percent} />
                <p className="mt-1.5 text-xs text-gray-500">{p.status}</p>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>
    </>
  );
};

export default UserDashboard;
