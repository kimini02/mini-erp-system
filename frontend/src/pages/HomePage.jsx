import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowUpRight,
  Bell,
  CheckCheck,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  FileCheck2,
  Home as HomeIcon,
  SquareKanban,
  Users,
} from 'lucide-react';
import { homeApi } from '@/api/home';
import { attendanceApi } from '@/api/attendance';
import { notificationApi } from '@/api/notification';
import { holidayApi } from '@/api/org';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { NOTIFICATION_TYPE_META, formatDays } from '@/lib/approval';
import { IN_RESULT_LABELS, OUT_RESULT_LABELS, timeOf } from '@/lib/attendance';
import { TASK_STATUS_META, dueText } from '@/lib/project';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Badge, Button, EmptyState, PageLoader, Progress } from '@/components/ui';
import homeBanner from '@/assets/home-banner.webp';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const md = (date) => `${Number(date.slice(5, 7))}.${date.slice(8, 10)}`;
const todayStr = () => new Date().toLocaleDateString('sv-SE');

/** 오늘 09:14 / 어제 17:40 / 10.10 15:02 */
function whenText(value) {
  if (!value) return '';
  const day = value.slice(0, 10);
  const time = value.slice(11, 16);
  const yesterday = new Date(Date.now() - 86400000).toLocaleDateString('sv-SE');
  if (day === todayStr()) return `오늘 ${time}`;
  if (day === yesterday) return `어제 ${time}`;
  return `${md(day)} ${time}`;
}

const LEAVE_WORDS = { LEAVE: '연차', HALF_AM: '오전 반차', HALF_PM: '오후 반차' };

/** 결재 양식 표시 (색을 채우고 흰 글씨) */
const FORM_TAG = {
  LEAVE: 'bg-blue-600',
  OVERTIME: 'bg-violet-600',
  ATTENDANCE_FIX: 'bg-amber-700',
};

/* ───────── 패널 접기 (이 브라우저에만 기억) ───────── */

const COLLAPSE_KEY = 'home.collapsed';

function useCollapsed() {
  const [closed, setClosed] = useState(() => {
    try {
      return new Set(JSON.parse(localStorage.getItem(COLLAPSE_KEY)) || []);
    } catch {
      return new Set();
    }
  });
  const save = (next) => {
    setClosed(next);
    try {
      localStorage.setItem(COLLAPSE_KEY, JSON.stringify([...next]));
    } catch {
      // 저장하지 못해도 화면은 그대로 동작합니다.
    }
  };
  const toggle = (id) => {
    const next = new Set(closed);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    save(next);
  };
  const open = (id) => {
    if (!closed.has(id)) return;
    const next = new Set(closed);
    next.delete(id);
    save(next);
  };
  return { isClosed: (id) => closed.has(id), toggle, open };
}

const iconButton =
  'flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 hover:text-gray-800';

function Panel({ id, icon: Icon, tint, title, extra, link, actions, collapse, className, children }) {
  const closed = collapse.isClosed(id);
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className={cn('flex min-w-0 scroll-mt-4 flex-col rounded-[14px] border border-gray-200 bg-white', className)}
    >
      <div className={cn('flex h-14 items-center gap-2.5 pl-4 pr-3', !closed && 'border-b border-gray-100')}>
        <span className={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-full', tint)}>
          {Icon && <Icon size={16} aria-hidden="true" />}
        </span>
        <h2 id={`${id}-title`} className="whitespace-nowrap text-[15px] font-bold text-gray-900">
          {title}
        </h2>
        {extra}
        <div className="ml-auto flex items-center">
          {actions}
          {link && (
            <Link to={link.to} aria-label={link.label} title={link.label} className={iconButton}>
              <ArrowUpRight size={16} />
            </Link>
          )}
          <button
            type="button"
            onClick={() => collapse.toggle(id)}
            aria-expanded={!closed}
            aria-controls={`${id}-body`}
            aria-label={`${title} ${closed ? '펼치기' : '접기'}`}
            className={iconButton}
          >
            {closed ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
          </button>
        </div>
      </div>
      <div id={`${id}-body`} hidden={closed} className={closed ? 'hidden' : 'flex flex-1 flex-col'}>
        {children}
      </div>
    </section>
  );
}

/* ───────── 내 정보 (프로필 · 출퇴근 · 숫자 칸) ───────── */

function todayStatus(today) {
  if (today.clockOutAt) return { label: '퇴근', tone: 'gray' };
  if (today.clockInAt) return { label: '근무 중', tone: 'green' };
  if (today.dayType === 'HOLIDAY') return { label: today.holidayName || '휴일', tone: 'gray' };
  if (today.leave) return { label: '휴가', tone: 'blue' };
  return { label: '출근 전', tone: 'amber' };
}

function ProfileCard({ user, initialToday, leave, tiles }) {
  const [today, setToday] = useState(initialToday);
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState('');
  const status = today ? todayStatus(today) : null;

  const act = async (kind) => {
    setBusy(kind);
    try {
      const next = kind === 'in' ? await attendanceApi.clockIn() : await attendanceApi.clockOut();
      setToday(next);
      const label = kind === 'in' ? IN_RESULT_LABELS[next.inResult] : OUT_RESULT_LABELS[next.outResult];
      toast.success(
        `${kind === 'in' ? '출근' : '퇴근'}했습니다 (${timeOf(kind === 'in' ? next.clockInAt : next.clockOutAt)} · ${label || '기록'})`,
      );
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBusy('');
      setAsking(false);
    }
  };
  const outBase = today && (today.dayType === 'HALF_PM' ? today.schedule.halfBoundary : today.schedule.endTime);
  const value = (v) => <b className="font-semibold text-gray-900">{v}</b>;
  const bar = (
    <span aria-hidden="true" className="mx-2 text-gray-300">
      |
    </span>
  );

  return (
    <section
      aria-label="내 정보"
      className="flex flex-[1_1_330px] flex-col gap-3.5 rounded-[14px] border border-gray-200 bg-white p-[18px]"
    >
      <div className="flex items-start gap-3.5">
        <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[14px] bg-blue-50 text-2xl font-bold text-blue-700">
          {user?.name?.trim()?.charAt(0) || '?'}
        </span>
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-2">
            <span className="truncate text-lg font-bold text-gray-900">{user?.name}</span>
            {status && (
              <Badge tone={status.tone} dot className="rounded-full">
                {status.label}
              </Badge>
            )}
          </p>
          <p className="truncate text-[13px] text-gray-500">
            {[user?.department?.name, user?.position].filter(Boolean).join(' · ')}
          </p>
          <p className="mt-1 text-[13px] leading-7 text-gray-500">
            사용 연차 {value(formatDays(leave.used))}
            {bar}남은 연차 <b className="font-semibold text-blue-700">{formatDays(leave.available)}</b>
            {today && (
              <>
                <br />
                이번 달 지각 {value(`${today.monthLateCount}회`)}
                {bar}결근 {value(`${today.monthAbsentCount ?? 0}회`)}
              </>
            )}
          </p>
        </div>
      </div>

      {today && (
        <div className="flex flex-col gap-2">
          <div className="flex min-h-12 items-center gap-3 rounded-[10px] bg-gray-50 py-1.5 pl-3.5 pr-1.5 text-[13px] tabular-nums text-gray-500">
            <span>출근 {value(timeOf(today.clockInAt) || '--:--')}</span>
            <span>
              퇴근{' '}
              <b className={cn('font-semibold', today.clockOutAt ? 'text-gray-900' : 'text-gray-500')}>
                {timeOf(today.clockOutAt) || '--:--'}
              </b>
            </span>
            {today.canClockIn && (
              <Button size="sm" className="ml-auto" loading={busy === 'in'} onClick={() => act('in')}>
                출근하기
              </Button>
            )}
            {today.canClockOut && !asking && (
              <Button
                size="sm"
                variant="secondary"
                className="ml-auto"
                loading={busy === 'out'}
                onClick={() => (today.earlyIfNow && !today.clockOutAt ? setAsking(true) : act('out'))}
              >
                {today.clockOutAt ? '퇴근 다시 찍기' : '퇴근하기'}
              </Button>
            )}
          </div>
          {asking && (
            <div
              role="alertdialog"
              aria-label="이른 퇴근 확인"
              className="flex flex-col gap-2.5 rounded-lg border border-amber-200 bg-amber-50 p-3"
            >
              <p className="text-[13px] text-amber-900">
                기준 퇴근 {outBase} 전입니다. 지금 퇴근하면 <b className="font-semibold">조퇴</b>로 기록됩니다. 반차라면
                휴가 신청을 먼저 해 주세요.
              </p>
              <div className="flex justify-end gap-2">
                <Button size="sm" variant="secondary" onClick={() => setAsking(false)}>
                  취소
                </Button>
                <Button
                  size="sm"
                  className="bg-amber-700 hover:bg-amber-800"
                  loading={busy === 'out'}
                  onClick={() => act('out')}
                >
                  그래도 퇴근
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {tiles.length > 0 && (
        <div className="mt-auto grid grid-cols-[repeat(auto-fit,minmax(140px,1fr))] gap-2">
          {tiles.map((t) => {
            const body = (
              <>
                <span className="inline-flex items-center gap-[7px] whitespace-nowrap text-[13px] text-gray-600">
                  <t.icon size={16} aria-hidden="true" /> {t.label}
                </span>
                <span className={cn('text-base font-bold tabular-nums', t.strong ? 'text-blue-700' : 'text-gray-900')}>
                  {t.value}
                </span>
              </>
            );
            const cls =
              'flex min-h-[46px] items-center justify-between gap-1.5 rounded-[10px] border border-gray-200 px-3 py-1.5 text-left transition-colors hover:border-blue-200 hover:bg-blue-50/40';
            return t.to ? (
              <Link key={t.key} to={t.to} className={cls}>
                {body}
              </Link>
            ) : (
              <button key={t.key} type="button" onClick={t.onClick} className={cls}>
                {body}
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}

/* ───────── 전자결재 ───────── */

function ApprovalsPanel({ approvals, collapse }) {
  const [tab, setTab] = useState(() => (approvals.todoCount > 0 || approvals.mine.length === 0 ? 'todo' : 'mine'));
  const items = tab === 'todo' ? approvals.todo : approvals.mine;
  const tabs = [
    { key: 'todo', label: '결재 대기', count: approvals.todoCount },
    { key: 'mine', label: '내가 올린', count: approvals.mine.length },
  ];
  return (
    <Panel
      id="home-approvals"
      icon={FileCheck2}
      tint="bg-blue-50 text-blue-700"
      title="전자결재"
      link={{ to: tab === 'todo' ? '/approvals' : '/approvals?box=mine', label: '결재함 열기' }}
      collapse={collapse}
      className="flex-[1_1_320px]"
    >
      <div className="px-4 pb-1 pt-3">
        <div role="tablist" aria-label="결재 목록" className="inline-flex gap-0.5 rounded-[10px] bg-gray-100 p-[3px]">
          {tabs.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={tab === t.key}
              onClick={() => setTab(t.key)}
              className={cn(
                'h-8 rounded-lg px-3 text-[13px]',
                tab === t.key
                  ? 'bg-white font-semibold text-gray-900 shadow-sm'
                  : 'font-medium text-gray-600 hover:text-gray-900',
              )}
            >
              {t.label}{' '}
              <span className={cn('tabular-nums', tab === t.key && t.key === 'todo' && t.count > 0 && 'text-blue-700')}>
                {t.count}
              </span>
            </button>
          ))}
        </div>
      </div>
      {items.length === 0 ? (
        <p className="px-4 py-8 text-center text-[13px] text-gray-500">
          {tab === 'todo' ? '결재할 문서가 없습니다.' : '진행 중인 문서가 없습니다.'}
        </p>
      ) : (
        <ul className="pb-1">
          {items.map((d, i) => (
            <li key={d.id} className={cn(i > 0 && 'border-t border-gray-100')}>
              <Link
                to={`/approvals/${d.id}`}
                className="flex items-start gap-2.5 px-4 py-3 text-gray-900 hover:bg-gray-50"
              >
                <span
                  className={cn(
                    'mt-px shrink-0 rounded-md px-2 py-px text-xs font-medium text-white',
                    FORM_TAG[d.form.code] || 'bg-gray-600',
                  )}
                >
                  {d.form.shortName}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{d.title}</span>
                  <span className="block truncate text-xs text-gray-500">
                    {tab === 'todo'
                      ? `${d.docNo} · ${d.drafter.name} ${d.drafter.title}`
                      : `${d.docNo} · ${d.currentApprover ? `${d.currentApprover.name} ${d.currentApprover.title} 차례` : '결재 중'}`}
                  </span>
                </span>
                <span className="shrink-0 text-xs text-gray-500">{whenText(d.submittedAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

/* ───────── 내 업무 ───────── */

function MyTasksPanel({ data, collapse }) {
  const { project, items } = data;
  return (
    <Panel
      id="home-tasks"
      icon={SquareKanban}
      tint="bg-violet-50 text-violet-700"
      title="내 업무"
      link={{
        to: project ? `/projects/${project.id}/board` : '/projects',
        label: project ? '보드 열기' : '프로젝트 열기',
      }}
      collapse={collapse}
      className="flex-[1_1_320px]"
    >
      {project && (
        <div className="px-4 pb-1.5 pt-3.5">
          <div className="flex justify-between gap-2 text-[13px]">
            <span className="truncate text-gray-600">{project.title}</span>
            <span className="font-bold tabular-nums text-gray-900">{project.progressPct}%</span>
          </div>
          <Progress value={project.progressPct} className="mt-1.5" />
          <p className="mt-1.5 text-xs text-gray-500">
            완료 {project.done} / 전체 {project.total}
            {project.overdue > 0 && (
              <>
                {' '}
                · <span className="font-semibold text-red-600">지연 {project.overdue}건</span>
              </>
            )}
          </p>
        </div>
      )}
      {items.length === 0 ? (
        <p className="px-4 py-8 text-center text-[13px] text-gray-500">맡은 업무가 없습니다.</p>
      ) : (
        <ul className="pb-1">
          {items.map((t) => {
            const due = dueText(t);
            const meta = TASK_STATUS_META[t.status];
            return (
              <li key={t.id} className="border-t border-gray-100">
                <Link
                  to={`/projects/${t.projectId}/board?taskId=${t.id}`}
                  className="flex min-h-12 items-center gap-2.5 px-4 text-gray-900 hover:bg-gray-50"
                >
                  <span className="w-11 shrink-0 text-xs tabular-nums text-gray-500">{t.key}</span>
                  <span className="min-w-0 flex-1 truncate font-medium">{t.title}</span>
                  <span
                    className={cn('shrink-0 whitespace-nowrap rounded-md px-2 py-px text-xs font-medium', meta.chip)}
                  >
                    {meta.label}
                  </span>
                  <span
                    className={cn(
                      'w-16 shrink-0 whitespace-nowrap text-right text-xs',
                      due.late ? 'font-semibold text-red-600' : 'text-gray-500',
                    )}
                  >
                    {due.text}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

/* ───────── 알림 ───────── */

function NoticesPanel({ notices, onReadAll, onOpen, collapse }) {
  return (
    <Panel
      id="home-notices"
      icon={Bell}
      tint="bg-rose-50 text-rose-600"
      title="알림"
      extra={notices.unread > 0 && <span className="font-bold tabular-nums text-red-600">{notices.unread}</span>}
      actions={
        notices.unread > 0 && (
          <button
            type="button"
            onClick={onReadAll}
            className="flex h-9 items-center gap-1 rounded-lg px-2.5 text-[13px] font-semibold text-blue-700 hover:bg-blue-50"
          >
            <CheckCheck size={14} aria-hidden="true" /> 모두 읽음
          </button>
        )
      }
      collapse={collapse}
      className="flex-[1_1_320px]"
    >
      {notices.items.length === 0 ? (
        <p className="px-4 py-8 text-center text-[13px] text-gray-500">새로운 알림이 없습니다.</p>
      ) : (
        <ul className="py-1">
          {notices.items.map((n) => {
            const meta = NOTIFICATION_TYPE_META[n.type] || { dot: 'bg-gray-400' };
            return (
              <li key={n.id}>
                <button
                  type="button"
                  onClick={() => onOpen(n)}
                  className="flex w-full items-start gap-2.5 px-4 py-2.5 text-left hover:bg-gray-50"
                >
                  <span className={cn('mt-[7px] h-2 w-2 shrink-0 rounded-full', n.read ? 'bg-gray-200' : meta.dot)} />
                  <span className="min-w-0 flex-1 leading-snug">
                    <span className={cn('block text-[13px]', n.read ? 'text-gray-600' : 'font-medium text-gray-900')}>
                      {meta.sentence ? meta.sentence(n.title) : n.title}
                    </span>
                    <span className="text-xs text-gray-500">{whenText(n.createdAt)}</span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

/* ───────── 이번 주 팀 ───────── */

function TeamWeekPanel({ week, collapse }) {
  const today = todayStr();
  return (
    <Panel
      id="home-team"
      icon={Users}
      tint="bg-emerald-50 text-emerald-700"
      title={`이번 주 ${week.department?.name || '우리 팀'}`}
      extra={
        <span className="text-xs tabular-nums text-gray-500">
          {md(week.from)} ~ {md(week.to)}
        </span>
      }
      collapse={collapse}
      className="flex-[999_1_560px]"
    >
      <div className="flex flex-1 flex-wrap gap-2 px-4 pb-4 pt-3.5">
        {week.days.map((d) => {
          const isToday = d.date === today;
          return (
            <div
              key={d.date}
              className={cn(
                'flex min-h-[116px] flex-[1_1_120px] flex-col gap-1.5 rounded-[10px] border p-2.5',
                isToday ? 'border-blue-200 bg-blue-50/70' : 'border-gray-100',
              )}
            >
              <p className={cn('text-xs', isToday ? 'font-bold text-blue-700' : 'font-semibold text-gray-700')}>
                {WEEKDAYS[new Date(`${d.date}T00:00:00`).getDay()]} {Number(d.date.slice(8, 10))}
                {isToday && ' · 오늘'}
              </p>
              {d.items.length === 0 && <span className="text-xs text-gray-500">모두 근무</span>}
              {d.items.map((e) =>
                e.type === 'TASK_DUE' ? (
                  <Link
                    key={`t${e.task.id}`}
                    to={`/projects/${e.project.id}/board?taskId=${e.task.id}`}
                    className={cn(
                      'rounded-md px-1.5 py-1 text-xs leading-snug text-gray-700 hover:bg-gray-200',
                      isToday ? 'border border-gray-200 bg-white' : 'bg-gray-100',
                    )}
                  >
                    {e.task.key} 마감
                  </Link>
                ) : (
                  <span
                    key={`l${e.user.id}${e.type}`}
                    className={cn(
                      'rounded-md px-1.5 py-1 text-xs leading-snug',
                      e.status === 'IN_PROGRESS'
                        ? 'border border-dashed border-blue-300 bg-white text-blue-700'
                        : 'bg-blue-100 text-blue-800',
                    )}
                  >
                    {e.user.name} {LEAVE_WORDS[e.type]}
                    {e.status === 'IN_PROGRESS' && ' (결재 중)'}
                  </span>
                ),
              )}
            </div>
          );
        })}
      </div>
    </Panel>
  );
}

/* ───────── 달력 ───────── */

function MiniCalendar({ marks }) {
  const today = todayStr();
  const [cursor, setCursor] = useState(today.slice(0, 7));
  const [picked, setPicked] = useState(today);
  const [holidays, setHolidays] = useState({});
  const year = Number(cursor.slice(0, 4));
  const month = Number(cursor.slice(5, 7));

  useEffect(() => {
    let alive = true;
    holidayApi
      .list(year)
      .then(
        (list) => alive && setHolidays((h) => ({ ...h, [year]: new Map((list || []).map((x) => [x.date, x.name])) })),
      )
      .catch(() => alive && setHolidays((h) => ({ ...h, [year]: new Map() })));
    return () => {
      alive = false;
    };
  }, [year]);

  const move = (delta) => {
    const d = new Date(year, month - 1 + delta, 1);
    setCursor(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  };
  const lead = new Date(year, month - 1, 1).getDay();
  const days = new Date(year, month, 0).getDate();
  const names = holidays[year];
  const cells = [
    ...Array(lead).fill(null),
    ...Array.from({ length: days }, (_, i) => `${cursor}-${String(i + 1).padStart(2, '0')}`),
  ];
  const pickedItems = marks.get(picked) || [];
  const pickedHoliday = holidays[Number(picked.slice(0, 4))]?.get(picked);

  return (
    <section
      aria-labelledby="home-cal-title"
      className="flex flex-[1_1_300px] flex-col gap-2 rounded-[14px] border border-gray-200 bg-white px-4 py-3.5"
    >
      <div className="flex items-center gap-1">
        <h2 id="home-cal-title" className="text-[15px] font-bold text-gray-900">
          {year}년 {month}월
        </h2>
        <button
          type="button"
          onClick={() => {
            setCursor(today.slice(0, 7));
            setPicked(today);
          }}
          className="ml-auto h-[30px] rounded-full border border-gray-200 px-2.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
        >
          오늘
        </button>
        <button
          type="button"
          aria-label="이전 달"
          onClick={() => move(-1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100"
        >
          <ChevronLeft size={16} />
        </button>
        <button
          type="button"
          aria-label="다음 달"
          onClick={() => move(1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100"
        >
          <ChevronRight size={16} />
        </button>
      </div>
      <div className="grid grid-cols-7 text-center text-[13px] tabular-nums">
        {WEEKDAYS.map((w, i) => (
          <span
            key={w}
            className={cn(
              'pb-1 text-xs font-semibold',
              i === 0 ? 'text-red-600' : i === 6 ? 'text-blue-600' : 'text-gray-500',
            )}
          >
            {w}
          </span>
        ))}
        {cells.map((date, i) => {
          if (!date) return <span key={`e${i}`} className="h-8" />;
          const dow = i % 7;
          const holiday = names?.get(date);
          const items = marks.get(date) || [];
          const hasTask = items.some((x) => x.kind === 'task');
          const hasLeave = items.some((x) => x.kind === 'leave');
          const isToday = date === today;
          const label = [
            `${month}월 ${Number(date.slice(8))}일`,
            holiday,
            hasTask && '업무 마감',
            hasLeave && '팀 휴가',
          ]
            .filter(Boolean)
            .join(', ');
          return (
            <button
              key={date}
              type="button"
              aria-label={label}
              aria-pressed={picked === date}
              onClick={() => setPicked(date)}
              className="flex h-8 flex-col items-center rounded-md hover:bg-gray-50"
            >
              <span
                className={cn(
                  'flex h-[26px] w-[26px] items-center justify-center rounded-full',
                  isToday ? 'bg-blue-600 font-bold text-white' : picked === date ? 'ring-1 ring-blue-300' : '',
                  !isToday && (dow === 0 || holiday) && 'text-red-600',
                  !isToday && dow === 6 && !holiday && 'text-blue-600',
                )}
              >
                {Number(date.slice(8))}
              </span>
              <span className="flex h-[5px] gap-[3px]" aria-hidden="true">
                {hasLeave && <span className="h-[5px] w-[5px] rounded-full bg-blue-700" />}
                {hasTask && <span className="h-[5px] w-[5px] rounded-full bg-amber-500" />}
              </span>
            </button>
          );
        })}
      </div>
      <div className="border-t border-gray-100 pt-2">
        <p className="text-xs font-semibold text-gray-500">
          {Number(picked.slice(5, 7))}월 {Number(picked.slice(8))}일 {WEEKDAYS[new Date(`${picked}T00:00:00`).getDay()]}
          요일
          {pickedHoliday && <span className="ml-1.5 text-red-600">{pickedHoliday}</span>}
        </p>
        {pickedItems.length === 0 ? (
          <p className="mt-1 text-[13px] text-gray-500">표시할 일정이 없습니다.</p>
        ) : (
          <ul className="mt-1 flex flex-col gap-1">
            {pickedItems.slice(0, 3).map((m) => (
              <li key={m.id} className="flex items-center gap-2 text-[13px]">
                <span
                  className={cn(
                    'h-1.5 w-1.5 shrink-0 rounded-full',
                    m.kind === 'task' ? 'bg-amber-500' : 'bg-blue-700',
                  )}
                />
                {m.to ? (
                  <Link to={m.to} className="truncate text-gray-900 hover:text-blue-700">
                    {m.label}
                  </Link>
                ) : (
                  <span className="truncate text-gray-900">{m.label}</span>
                )}
              </li>
            ))}
            {pickedItems.length > 3 && <li className="text-xs text-gray-500">외 {pickedItems.length - 3}건</li>}
          </ul>
        )}
      </div>
      <div className="mt-auto flex gap-3.5 text-xs text-gray-500">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
          업무 마감
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-blue-700" />팀 휴가
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="font-semibold text-red-600">9</span>공휴일
        </span>
      </div>
    </section>
  );
}

/** 달력 점: 내 업무 마감(목록의 5건)과 이번 주 팀 일정 */
function buildMarks(data) {
  const map = new Map();
  const seen = new Set();
  const add = (date, item) => {
    if (!date || seen.has(item.id)) return;
    seen.add(item.id);
    if (!map.has(date)) map.set(date, []);
    map.get(date).push(item);
  };
  data.myTasks?.items.forEach((t) =>
    add(t.endDate, {
      id: `t${t.id}`,
      kind: 'task',
      label: `${t.key} ${t.title} 마감`,
      to: `/projects/${t.projectId}/board?taskId=${t.id}`,
    }),
  );
  data.teamWeek?.days.forEach((d) =>
    d.items.forEach((e) => {
      if (e.type === 'TASK_DUE') {
        add(d.date, {
          id: `t${e.task.id}`,
          kind: 'task',
          label: `${e.task.key} ${e.task.title} 마감`,
          to: `/projects/${e.project.id}/board?taskId=${e.task.id}`,
        });
      } else {
        add(d.date, {
          id: `l${d.date}${e.user.id}${e.type}`,
          kind: 'leave',
          label: `${e.user.name} ${LEAVE_WORDS[e.type]}${e.status === 'IN_PROGRESS' ? ' (결재 중)' : ''}`,
        });
      }
    }),
  );
  return map;
}

/* ───────── 홈 ───────── */

export default function HomePage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const refreshCounts = useApprovalStore((s) => s.refresh);
  const collapse = useCollapsed();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(
    () =>
      homeApi
        .get()
        .then((d) => {
          setData(d);
          setError('');
        })
        .catch((err) => setError(getErrorMessage(err))),
    [],
  );
  useEffect(() => {
    load();
  }, [load]);
  const marks = useMemo(() => (data ? buildMarks(data) : new Map()), [data]);

  const readAll = async () => {
    await notificationApi.readAll().catch(() => {});
    setData((d) => ({
      ...d,
      notifications: { unread: 0, items: d.notifications.items.map((n) => ({ ...n, read: true })) },
    }));
    refreshCounts();
  };
  const openNotice = async (n) => {
    if (!n.read) {
      await notificationApi.read(n.id).catch(() => {});
      refreshCounts();
    }
    navigate(n.link);
  };
  /** 숫자 칸에서 아래 패널로: 접혀 있으면 펼치고 그 위치로 */
  const reveal = (id) => {
    collapse.open(id);
    const smooth = !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    requestAnimationFrame(() =>
      document.getElementById(id)?.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'start' }),
    );
  };

  if (error && !data) return <EmptyState icon={HomeIcon} title="홈을 불러오지 못했습니다" description={error} />;
  if (!data) return <PageLoader />;

  const teamLeaves = data.teamWeek
    ? data.teamWeek.days.reduce((sum, d) => sum + d.items.filter((e) => e.type !== 'TASK_DUE').length, 0)
    : null;
  const tiles = [
    data.approvals && {
      key: 'apv',
      icon: FileCheck2,
      label: '받은 결재',
      value: data.approvals.todoCount,
      strong: data.approvals.todoCount > 0,
      to: '/approvals',
    },
    data.myTasks && {
      key: 'task',
      icon: SquareKanban,
      label: '받은 업무',
      value: data.myTasks.count ?? data.myTasks.items.length,
      onClick: () => reveal('home-tasks'),
    },
    {
      key: 'ntf',
      icon: Bell,
      label: '새 알림',
      value: data.notifications.unread,
      onClick: () => reveal('home-notices'),
    },
    teamLeaves !== null && {
      key: 'team',
      icon: Users,
      label: '팀 휴가',
      value: teamLeaves,
      onClick: () => reveal('home-team'),
    },
  ].filter(Boolean);

  return (
    <div className="mx-auto flex max-w-[1280px] flex-col gap-4">
      <h1 className="sr-only">홈</h1>

      <div className="flex flex-wrap items-stretch gap-4">
        <ProfileCard user={user} initialToday={data.today} leave={data.leave} tiles={tiles} />
        {/* 배너 글씨가 잘리지 않게: 프로필 옆에 640px 이상 남을 때만 나란히, 아니면 한 줄 전체 */}
        <figure className="relative m-0 aspect-[1970/638] min-w-0 flex-[3_1_640px] overflow-hidden rounded-[14px] bg-gray-200">
          <img
            src={homeBanner}
            alt="Workflow 소개 배너: 더 빠르게 협업하고! 더 쉽게 연결하고! 업무 흐름을 한 번에 Workflow 하라!"
            className="absolute inset-0 h-full w-full object-cover"
          />
        </figure>
      </div>

      <div className="flex flex-wrap items-stretch gap-4">
        {data.approvals && <ApprovalsPanel approvals={data.approvals} collapse={collapse} />}
        {data.myTasks && <MyTasksPanel data={data.myTasks} collapse={collapse} />}
        <NoticesPanel notices={data.notifications} onReadAll={readAll} onOpen={openNotice} collapse={collapse} />
      </div>

      <div className="flex flex-wrap items-stretch gap-4">
        {data.teamWeek && <TeamWeekPanel week={data.teamWeek} collapse={collapse} />}
        <MiniCalendar marks={marks} />
      </div>
    </div>
  );
}
