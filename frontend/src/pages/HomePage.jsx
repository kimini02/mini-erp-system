import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CalendarPlus, CheckCheck, Clock3, FilePlus2, Home as HomeIcon, MoonStar } from 'lucide-react';
import { homeApi } from '@/api/m4';
import { attendanceApi } from '@/api/m3';
import { notificationApi } from '@/api/m2';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { MENU } from '@/lib/permissions';
import { NOTIFICATION_TYPE_META, formatDays } from '@/lib/approval';
import { IN_RESULT_LABELS, OUT_RESULT_LABELS, timeOf } from '@/lib/attendance';
import { TASK_STATUS_META, dueText } from '@/lib/project';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Badge, Button, Card, CardHeader, EmptyState, PageLoader, Progress } from '@/components/ui';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const longDate = (d = new Date()) => `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAYS[d.getDay()]}요일`;
const md = (date) => `${Number(date.slice(5, 7))}.${date.slice(8, 10)}`;

/** 오늘 09:14 / 어제 17:40 / 10.10 15:02 */
function whenText(value) {
  if (!value) return '';
  const day = value.slice(0, 10);
  const time = value.slice(11, 16);
  const today = new Date().toLocaleDateString('sv-SE');
  const yesterday = new Date(Date.now() - 86400000).toLocaleDateString('sv-SE');
  if (day === today) return `오늘 ${time}`;
  if (day === yesterday) return `어제 ${time}`;
  return `${md(day)} ${time}`;
}

const panel = 'rounded-xl border border-gray-200 bg-white';

/* ───────── 오늘 근무 ───────── */

function TodayWidget({ initial }) {
  const [today, setToday] = useState(initial);
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState('');
  if (!today) return null;

  const status = today.clockOutAt
    ? { label: '퇴근', tone: 'gray' }
    : today.clockInAt
      ? { label: '근무 중', tone: 'green' }
      : today.dayType === 'HOLIDAY'
        ? { label: today.holidayName || '휴일', tone: 'gray' }
        : today.leave
          ? { label: '휴가', tone: 'blue' }
          : { label: '출근 전', tone: 'amber' };

  const act = async (kind) => {
    setBusy(kind);
    try {
      const next = kind === 'in' ? await attendanceApi.clockIn() : await attendanceApi.clockOut();
      setToday(next);
      const label = kind === 'in' ? IN_RESULT_LABELS[next.inResult] : OUT_RESULT_LABELS[next.outResult];
      toast.success(`${kind === 'in' ? '출근' : '퇴근'}했습니다 (${timeOf(kind === 'in' ? next.clockInAt : next.clockOutAt)} · ${label || '기록'})`);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBusy('');
      setAsking(false);
    }
  };
  const outBase = today.dayType === 'HALF_PM' ? today.schedule.halfBoundary : today.schedule.endTime;

  return (
    <section aria-labelledby="home-att" className={cn(panel, 'flex flex-col gap-3.5 px-5 py-[18px]')}>
      <div className="flex items-center justify-between">
        <h2 id="home-att" className="text-[15px] font-semibold text-gray-900">오늘 근무</h2>
        <Badge tone={status.tone} dot>{status.label}</Badge>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {[['출근', today.clockInAt, today.inResult, IN_RESULT_LABELS], ['퇴근', today.clockOutAt, today.outResult, OUT_RESULT_LABELS]].map(([label, at, result, labels]) => (
          <div key={label} className="rounded-lg bg-gray-50 px-3 py-2.5">
            <p className="text-xs text-gray-500">{label}</p>
            <p className={cn('mt-0.5 text-xl font-semibold tabular-nums', at ? 'text-gray-900' : 'text-gray-300')}>{timeOf(at) || '--:--'}</p>
            {result && result !== 'ON_TIME' && result !== 'NONE' && <p className="text-xs text-amber-700">{labels[result]}</p>}
          </div>
        ))}
      </div>
      {today.canClockIn && <Button block icon={Clock3} loading={busy === 'in'} onClick={() => act('in')}>출근하기</Button>}
      {today.canClockOut && !asking && (
        <Button block variant="secondary" loading={busy === 'out'} onClick={() => (today.earlyIfNow && !today.clockOutAt ? setAsking(true) : act('out'))}>
          {today.clockOutAt ? '퇴근 다시 찍기' : '퇴근하기'}
        </Button>
      )}
      {asking && (
        <div role="alertdialog" aria-label="이른 퇴근 확인" className="flex flex-col gap-2.5 rounded-lg border border-amber-200 bg-amber-50 p-3">
          <p className="text-[13px] text-amber-900">
            기준 퇴근 {outBase} 전입니다. 지금 퇴근하면 <b className="font-semibold">조퇴</b>로 기록됩니다. 반차라면 휴가 신청을 먼저 해 주세요.
          </p>
          <div className="flex justify-end gap-2">
            <Button size="sm" variant="secondary" onClick={() => setAsking(false)}>취소</Button>
            <Button size="sm" className="bg-amber-700 hover:bg-amber-800" loading={busy === 'out'} onClick={() => act('out')}>그래도 퇴근</Button>
          </div>
        </div>
      )}
      <p className="mt-auto text-xs text-gray-500">
        {today.schedule.departmentName || '회사'} 근무시간 {today.schedule.startTime} ~ {today.schedule.endTime} · 이번 달 지각 {today.monthLateCount}회
      </p>
    </section>
  );
}

/* ───────── 결재 ───────── */

function ApprovalsWidget({ approvals }) {
  if (!approvals) return null;
  const showTodo = approvals.todoCount > 0 || approvals.mine.length === 0;
  const items = showTodo ? approvals.todo : approvals.mine;
  return (
    <section aria-labelledby="home-apv" className={cn(panel, 'flex flex-col gap-2.5 px-5 py-[18px]')}>
      <div className="flex items-center justify-between">
        <h2 id="home-apv" className="text-[15px] font-semibold text-gray-900">
          {showTodo ? '결재 대기' : '내가 올린 결재'}{' '}
          <span className={cn('font-semibold tabular-nums', showTodo && approvals.todoCount > 0 ? 'text-blue-700' : 'text-gray-400')}>
            {showTodo ? approvals.todoCount : approvals.mine.length}
          </span>
        </h2>
        <Link to={showTodo ? '/approvals' : '/approvals?box=mine'} className="text-[13px] font-medium text-blue-700 hover:underline">결재함</Link>
      </div>
      {items.length === 0 ? (
        <p className="py-6 text-center text-[13px] text-gray-500">결재할 문서가 없습니다.</p>
      ) : (
        <ul className="flex flex-col">
          {items.map((d) => (
            <li key={d.id} className="border-t border-gray-100">
              <Link to={`/approvals/${d.id}`} className="flex min-h-[52px] items-center gap-2.5 text-gray-900 hover:text-blue-700">
                <span className="whitespace-nowrap rounded-md bg-gray-100 px-1.5 py-px text-xs font-medium text-gray-700">{d.form.shortName}</span>
                <span className="min-w-0 flex-1 leading-snug">
                  <span className="block truncate font-medium">{d.title}</span>
                  <span className="text-xs text-gray-500">
                    {showTodo
                      ? `${d.drafter.name} ${d.drafter.title} · ${whenText(d.submittedAt)}`
                      : d.currentApprover ? `결재 중 · ${d.currentApprover.name} ${d.currentApprover.title} 차례` : '결재 중'}
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/* ───────── 내 연차 ───────── */

function LeaveWidget({ leave }) {
  if (!leave) return null;
  const total = Math.max(leave.granted, leave.used + leave.reserved + Math.max(leave.available, 0), 1);
  return (
    <section aria-labelledby="home-lv" className={cn(panel, 'flex flex-col gap-3 px-5 py-[18px]')}>
      <div className="flex items-center justify-between">
        <h2 id="home-lv" className="text-[15px] font-semibold text-gray-900">내 연차</h2>
        <Link to="/leaves" className="text-[13px] font-medium text-blue-700 hover:underline">원장 보기</Link>
      </div>
      <div>
        <p className="text-[13px] font-medium text-blue-700">사용 가능</p>
        <p className="text-[32px] font-semibold leading-tight tabular-nums text-blue-700">
          {formatDays(leave.available).replace('일', '')}<span className="ml-0.5 text-[15px] font-medium">일</span>
        </p>
      </div>
      <div role="img" aria-label={`사용 ${formatDays(leave.used)}, 결재 중 ${formatDays(leave.reserved)}`} className="flex h-2 overflow-hidden rounded-full bg-gray-100">
        <span className="bg-blue-600" style={{ width: `${(leave.used / total) * 100}%` }} />
        <span className="bg-blue-300" style={{ width: `${(leave.reserved / total) * 100}%` }} />
      </div>
      <p className="text-xs text-gray-500">부여 {formatDays(leave.granted)} · 사용 {formatDays(leave.used)} · 결재 중 {formatDays(leave.reserved)}</p>
    </section>
  );
}

/* ───────── 이번 주 팀 ───────── */

const LEAVE_WORDS = { LEAVE: '연차', HALF_AM: '오전 반차', HALF_PM: '오후 반차' };

function TeamWeekWidget({ week }) {
  if (!week) return null;
  const today = new Date().toLocaleDateString('sv-SE');
  return (
    <Card aria-labelledby="home-week">
      <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
        <h2 id="home-week" className="text-[15px] font-semibold text-gray-900">이번 주 {week.department?.name || '우리 팀'}</h2>
        <span className="text-xs tabular-nums text-gray-500">{md(week.from)} ~ {md(week.to)}</span>
      </div>
      <div className="grid grid-cols-5 gap-2 px-5 pb-4 pt-3">
        {week.days.map((d) => {
          const isToday = d.date === today;
          return (
            <div key={d.date} className={cn('flex min-h-[132px] flex-col gap-1.5 rounded-lg border p-2.5', isToday ? 'border-blue-200 bg-blue-50' : 'border-gray-100 bg-white')}>
              <p className={cn('text-xs font-semibold', isToday ? 'text-blue-700' : 'text-gray-700')}>
                {WEEKDAYS[new Date(`${d.date}T00:00:00`).getDay()]} {Number(d.date.slice(8, 10))}
              </p>
              {d.items.length === 0 && <span className="text-xs text-gray-400">모두 근무</span>}
              {d.items.map((e, i) => (e.type === 'TASK_DUE' ? (
                <Link key={i} to={`/projects/${e.project.id}/board?taskId=${e.task.id}`} className="rounded-md bg-gray-100 px-1.5 py-1 text-xs leading-snug text-gray-700 hover:bg-gray-200">
                  {e.task.key} 마감
                </Link>
              ) : (
                <span
                  key={i}
                  className={cn('rounded-md px-1.5 py-1 text-xs leading-snug', e.status === 'IN_PROGRESS' ? 'border border-dashed border-blue-300 bg-white text-blue-700' : 'bg-blue-100 text-blue-800')}
                >
                  {e.user.name} {LEAVE_WORDS[e.type]}{e.status === 'IN_PROGRESS' && ' (결재 중)'}
                </span>
              )))}
            </div>
          );
        })}
      </div>
    </Card>
  );
}

/* ───────── 내 업무 ───────── */

function MyTasksWidget({ data }) {
  if (!data) return null;
  const { project, items } = data;
  return (
    <Card aria-labelledby="home-task">
      <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
        <h2 id="home-task" className="text-[15px] font-semibold text-gray-900">내 업무</h2>
        <Link to={project ? `/projects/${project.id}/board` : '/projects'} className="text-[13px] font-medium text-blue-700 hover:underline">
          {project ? '보드 열기' : '프로젝트'}
        </Link>
      </div>
      {project && (
        <div className="px-5 pb-1 pt-3.5">
          <div className="flex justify-between text-[13px]">
            <span className="text-gray-600">{project.title}</span>
            <span className="font-semibold tabular-nums text-gray-900">{project.progressPct}%</span>
          </div>
          <Progress value={project.progressPct} className="mt-1.5 h-1.5" />
          <p className="mt-1.5 text-xs text-gray-500">완료 {project.done} / 전체 {project.total}{project.overdue > 0 && ` · 지연 ${project.overdue}건`}</p>
        </div>
      )}
      {items.length === 0 ? (
        <p className="px-5 py-8 text-center text-[13px] text-gray-500">맡은 업무가 없습니다.</p>
      ) : (
        <ul className="px-5 pb-3 pt-1">
          {items.map((t) => {
            const due = dueText(t);
            const meta = TASK_STATUS_META[t.status];
            return (
              <li key={t.id} className="border-t border-gray-100">
                <Link to={`/projects/${t.projectId}/board?taskId=${t.id}`} className="flex min-h-[52px] items-center gap-2.5 text-gray-900 hover:text-blue-700">
                  <span className="w-12 shrink-0 text-xs tabular-nums text-gray-500">{t.key}</span>
                  <span className="min-w-0 flex-1 truncate font-medium">{t.title}</span>
                  <span className={cn('whitespace-nowrap rounded-md px-2 py-px text-xs font-medium', meta.chip)}>{meta.label}</span>
                  <span className={cn('w-[72px] whitespace-nowrap text-right text-xs', due.late ? 'font-semibold text-red-600' : 'text-gray-500')}>{due.text}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

/* ───────── 알림 ───────── */

function NoticesWidget({ notices, onReadAll, onOpen }) {
  return (
    <Card aria-labelledby="home-ntf">
      <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
        <h2 id="home-ntf" className="text-[15px] font-semibold text-gray-900">
          알림 {notices.unread > 0 && <span className="font-semibold tabular-nums text-red-600">{notices.unread}</span>}
        </h2>
        {notices.unread > 0 && (
          <button type="button" onClick={onReadAll} className="flex h-8 items-center gap-1 rounded-md px-2 text-[13px] font-medium text-blue-700 hover:bg-blue-50">
            <CheckCheck size={14} /> 모두 읽음
          </button>
        )}
      </div>
      {notices.items.length === 0 ? (
        <p className="px-5 py-8 text-center text-[13px] text-gray-500">새로운 알림이 없습니다.</p>
      ) : (
        <ul className="px-5 pb-3 pt-1">
          {notices.items.map((n) => {
            const meta = NOTIFICATION_TYPE_META[n.type] || { dot: 'bg-gray-400' };
            return (
              <li key={n.id} className="border-t border-gray-100">
                <button type="button" onClick={() => onOpen(n)} className="flex w-full items-start gap-2.5 py-3 text-left">
                  <span className={cn('mt-[7px] h-2 w-2 shrink-0 rounded-full', n.read ? 'bg-gray-200' : meta.dot)} />
                  <span className="min-w-0 flex-1 leading-snug">
                    <span className={cn('block text-[13px]', n.read ? 'text-gray-600' : 'font-medium text-gray-900')}>{meta.sentence ? meta.sentence(n.title) : n.title}</span>
                    <span className="text-xs text-gray-400">{whenText(n.createdAt)}</span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

/* ───────── 홈 ───────── */

export default function HomePage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const canDraft = useAuthStore((s) => s.can(MENU.APPROVAL_DRAFT, 'C'));
  const refreshCounts = useApprovalStore((s) => s.refresh);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => homeApi.get().then((d) => { setData(d); setError(''); }).catch((err) => setError(getErrorMessage(err))), []);
  useEffect(() => { load(); }, [load]);

  const readAll = async () => {
    await notificationApi.readAll().catch(() => {});
    setData((d) => ({ ...d, notifications: { unread: 0, items: d.notifications.items.map((n) => ({ ...n, read: true })) } }));
    refreshCounts();
  };
  const openNotice = async (n) => {
    if (!n.read) {
      await notificationApi.read(n.id).catch(() => {});
      refreshCounts();
    }
    navigate(n.link);
  };

  if (error && !data) return <EmptyState icon={HomeIcon} title="홈을 불러오지 못했습니다" description={error} />;
  if (!data) return <PageLoader />;

  return (
    <div className="mx-auto flex max-w-[1280px] flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-[13px] text-gray-500">{longDate()}</p>
          <h1 className="mt-1 text-[22px] font-semibold tracking-tight text-gray-900">
            안녕하세요, {user?.name} {user?.position || ''}님
          </h1>
        </div>
        {canDraft && (
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" icon={CalendarPlus} onClick={() => navigate('/approvals/new?form=LEAVE')}>휴가 신청</Button>
            <Button variant="secondary" icon={MoonStar} onClick={() => navigate('/approvals/new?form=OVERTIME')}>특근 신청</Button>
            <Button icon={FilePlus2} onClick={() => navigate('/approvals/new')}>기안하기</Button>
          </div>
        )}
      </div>

      <div className="grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <TodayWidget initial={data.today} />
        <ApprovalsWidget approvals={data.approvals} />
        <LeaveWidget leave={data.leave} />
      </div>

      <TeamWeekWidget week={data.teamWeek} />

      <div className="grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(min(100%,380px),1fr))]">
        <MyTasksWidget data={data.myTasks} />
        <NoticesWidget notices={data.notifications} onReadAll={readAll} onOpen={openNotice} />
      </div>
    </div>
  );
}
