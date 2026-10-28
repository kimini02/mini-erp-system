import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react';
import { attendanceApi } from '@/api/attendance';
import { calendarApi } from '@/api/calendar';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import {
  CELL_META,
  DAY_TYPE_LABELS,
  FIXABLE_CODES,
  IN_RESULT_LABELS,
  OUT_RESULT_LABELS,
  dayCode,
  monthLabel,
  shiftDate,
  shiftMonth,
  thisMonth,
  timeOf,
  todayLocal,
} from '@/lib/attendance';
import { getErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/cn';
import { Button, Card, CardBody, CardHeader, EmptyState, PageHeader, PageLoader } from '@/components/ui';

/*
 * 내 일정 캘린더: 한 달 동안의 내 휴가·특근·근태를 한 달력에 모읍니다.
 * - 근태(휴가·공휴일·출퇴근 판정): GET /attendance/me — 내 근태 화면과 같은 칸 규칙(dayCode)
 * - 승인된 휴가·특근 일정: GET /calendar/events — BR-C001(승인된 일정만)
 * 출퇴근 버튼은 홈·내 근태에 있으므로 여기서는 보여 주기와 신청 바로가기만 합니다.
 */

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 달력 칸에 이름표로 보여 줄 근태 코드 (정상 출근은 점으로만, 미래·기록 없음·휴일은 표시 안 함)
const CHIP_CODES = new Set(['LATE', 'EARLY', 'MISSING', 'ABSENT', 'LEAVE', 'HALF_AM', 'HALF_PM', 'HOLIDAY_WORK']);

const monthDates = (month) => {
  const [year, mon] = month.split('-').map(Number);
  const last = new Date(year, mon, 0).getDate();
  return Array.from({ length: last }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`);
};
const weekdayOf = (date) => new Date(`${date}T00:00:00`).getDay();
const dayLabel = (date) =>
  `${Number(date.slice(5, 7))}월 ${Number(date.slice(8, 10))}일 (${WEEKDAYS[weekdayOf(date)]})`;
// 서버 일정 제목은 "연차 - {종류}", "특근 - {사유}"라서 뒷부분만 보여 줍니다.
const titleDetail = (title) => title.split(' - ').slice(1).join(' - ') || title;

/** 날짜 → 그날의 휴가·특근 일정 (여러 날 휴가는 날마다 넣음) */
function eventsByDate(events, month) {
  const byDate = new Map();
  for (const event of events) {
    const end = event.end.slice(0, 10);
    for (let date = event.start.slice(0, 10); date <= end; date = shiftDate(date, 1)) {
      if (!date.startsWith(month)) continue;
      if (!byDate.has(date)) byDate.set(date, []);
      byDate.get(date).push(event);
    }
  }
  return byDate;
}

function DayCell({ date, day, events, isToday, selected, onSelect }) {
  const code = day ? dayCode(day) : 'FUTURE';
  const weekday = weekdayOf(date);
  const red = weekday === 0 || Boolean(day?.holidayName);
  const overtimes = events.filter((e) => e.type === 'OVERTIME');
  return (
    <button
      type="button"
      onClick={() => onSelect(date)}
      aria-pressed={selected}
      aria-label={dayLabel(date)}
      className={cn(
        'flex min-h-[92px] flex-col gap-1 border-b border-r border-gray-100 p-1.5 text-left transition-colors hover:bg-gray-50',
        selected && 'bg-blue-50/60 hover:bg-blue-50',
      )}
    >
      <span className="flex items-center justify-between">
        <span
          className={cn(
            'flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium',
            isToday
              ? 'bg-blue-600 text-white'
              : red
                ? 'text-red-500'
                : weekday === 6
                  ? 'text-blue-500'
                  : 'text-gray-700',
          )}
        >
          {Number(date.slice(8, 10))}
        </span>
        {code === 'ON_TIME' && <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" title="정상 출근" />}
      </span>
      {day?.holidayName && <span className="truncate text-[11px] text-red-500">{day.holidayName}</span>}
      {CHIP_CODES.has(code) && (
        <span className={cn('truncate rounded px-1.5 py-0.5 text-[11px] font-medium', CELL_META[code].className)}>
          {CELL_META[code].label}
          {day?.fixDocument && ' · 정정'}
        </span>
      )}
      {overtimes.map((e) => (
        <span
          key={e.eventId}
          className="truncate rounded bg-indigo-50 px-1.5 py-0.5 text-[11px] font-medium text-indigo-700"
        >
          특근 {timeOf(e.start)}~{timeOf(e.end)}
        </span>
      ))}
    </button>
  );
}

function AttendanceRows({ day }) {
  if (day.absent) {
    return (
      <div className="flex justify-between">
        <dt className="text-gray-500">근태</dt>
        <dd className="font-medium text-red-600">결근</dd>
      </div>
    );
  }
  return (
    <>
      <div className="flex justify-between">
        <dt className="text-gray-500">출근</dt>
        <dd className="text-gray-800">
          {timeOf(day.clockInAt) || '—'}
          {day.inResult && <span className="ml-1.5 text-gray-500">{IN_RESULT_LABELS[day.inResult]}</span>}
        </dd>
      </div>
      <div className="flex justify-between">
        <dt className="text-gray-500">퇴근</dt>
        <dd className="text-gray-800">
          {timeOf(day.clockOutAt) || '—'}
          {day.outResult && <span className="ml-1.5 text-gray-500">{OUT_RESULT_LABELS[day.outResult]}</span>}
        </dd>
      </div>
    </>
  );
}

/** 날짜를 고르면 오른쪽에 그날의 근태·일정과 신청 바로가기 */
function DayPanel({ date, day, events, today, canDraft }) {
  const code = day ? dayCode(day, today) : 'FUTURE';
  const leaves = events.filter((e) => e.type === 'LEAVE');
  const overtimes = events.filter((e) => e.type === 'OVERTIME');
  const hasAttendance = Boolean(day && (day.clockInAt || day.absent || FIXABLE_CODES.has(code)));
  const actions = [];
  if (canDraft && date >= today && day?.dayType === 'WORKDAY') {
    actions.push({ to: `/approvals/new?form=LEAVE&date=${date}`, label: '이 날 휴가 신청' });
  }
  if (canDraft && date >= today) {
    actions.push({ to: `/approvals/new?form=OVERTIME&date=${date}`, label: '특근 신청' });
  }
  if (canDraft && date < today && FIXABLE_CODES.has(code) && !day?.fixDocument) {
    actions.push({ to: `/approvals/new?form=ATTENDANCE_FIX&date=${date}`, label: '근태 정정 신청' });
  }

  return (
    <Card>
      <CardHeader
        title={dayLabel(date)}
        description={[day && DAY_TYPE_LABELS[day.dayType], day?.holidayName].filter(Boolean).join(' · ') || undefined}
      />
      <CardBody className="space-y-4 text-sm">
        {hasAttendance && (
          <dl className="space-y-1.5">
            <AttendanceRows day={day} />
            {day.fixDocument && (
              <div className="flex justify-between">
                <dt className="text-gray-500">정정</dt>
                <dd>
                  <Link to={`/approvals/${day.fixDocument.id}`} className="text-blue-600 hover:underline">
                    {day.fixDocument.docNo}
                  </Link>
                </dd>
              </div>
            )}
          </dl>
        )}

        {leaves.length + overtimes.length > 0 && (
          <ul className="space-y-1.5">
            {leaves.map((e) => (
              <li key={`l${e.eventId}`} className="flex items-center gap-2 text-gray-800">
                <span className="h-2 w-2 rounded-full bg-blue-500" />
                휴가 · {titleDetail(e.title)}
              </li>
            ))}
            {overtimes.map((e) => (
              <li key={`o${e.eventId}`} className="flex items-center gap-2 text-gray-800">
                <span className="h-2 w-2 rounded-full bg-indigo-500" />
                특근 {timeOf(e.start)}~{timeOf(e.end)} · {titleDetail(e.title)}
              </li>
            ))}
          </ul>
        )}

        {!hasAttendance && leaves.length + overtimes.length === 0 && (
          <p className="text-gray-500">기록된 일정이 없습니다.</p>
        )}

        {actions.length > 0 && (
          <div className="flex flex-wrap gap-2 border-t border-gray-100 pt-4">
            {actions.map((a) => (
              <Link key={a.to} to={a.to}>
                <Button size="sm" variant="secondary">
                  {a.label}
                </Button>
              </Link>
            ))}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function MonthSummary({ summary, overtimeCount }) {
  const items = [
    ['근무일', summary.workDays, '일'],
    ['출근', summary.present, '일'],
    ['지각', summary.late, '회'],
    ['조퇴', summary.early, '회'],
    ['결근', summary.absent, '일'],
    ['휴가', summary.leaveDays, '일'],
    ['특근', overtimeCount, '건'],
  ];
  return (
    <dl className="grid grid-cols-4 gap-x-4 gap-y-2 sm:grid-cols-7">
      {items.map(([label, value, unit]) => (
        <div key={label}>
          <dt className="text-xs text-gray-500">{label}</dt>
          <dd className="text-sm font-semibold text-gray-900">
            {value ?? 0}
            <span className="ml-0.5 text-xs font-normal text-gray-500">{unit}</span>
          </dd>
        </div>
      ))}
    </dl>
  );
}

const LEGEND = [
  ['LEAVE', '휴가'],
  ['HALF_AM', '반차'],
  ['LATE', '지각'],
  ['EARLY', '조퇴'],
  ['MISSING', '퇴근 누락'],
  ['ABSENT', '결근'],
];

function Legend() {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-2 px-5 py-3 text-xs text-gray-500">
      <span className="flex items-center gap-1.5">
        <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
        정상 출근
      </span>
      {LEGEND.map(([code, label]) => (
        <span key={code} className="flex items-center gap-1.5">
          <span className={cn('h-3 w-3 rounded', CELL_META[code].className)} />
          {label}
        </span>
      ))}
      <span className="flex items-center gap-1.5">
        <span className="h-3 w-3 rounded bg-indigo-50" />
        특근
      </span>
    </div>
  );
}

const loadMonth = async (month, canAttendance) => {
  const [year, mon] = month.split('-').map(Number);
  const [attendance, events] = await Promise.allSettled([
    canAttendance ? attendanceApi.me(month) : Promise.resolve(null),
    calendarApi.events(year, mon),
  ]);
  return {
    attendance: attendance.status === 'fulfilled' ? attendance.value : null,
    attendanceError: attendance.status === 'rejected' ? getErrorMessage(attendance.reason) : '',
    events: events.status === 'fulfilled' ? events.value || [] : [],
    eventsError: events.status === 'rejected' ? getErrorMessage(events.reason) : '',
  };
};

export default function CalendarPage() {
  const canAttendance = useAuthStore((s) => s.can(MENU.MY_ATTENDANCE));
  const canDraft = useAuthStore((s) => s.can(MENU.APPROVAL_DRAFT, 'C'));
  const today = todayLocal();
  const [month, setMonth] = useState(thisMonth());
  const [selected, setSelected] = useState(today);
  const [data, setData] = useState(null);

  useEffect(() => {
    let alive = true;
    setData(null);
    loadMonth(month, canAttendance).then((result) => alive && setData(result));
    return () => {
      alive = false;
    };
  }, [month, canAttendance]);

  const dates = useMemo(() => monthDates(month), [month]);
  const dayMap = useMemo(() => new Map((data?.attendance?.days || []).map((d) => [d.date, d])), [data]);
  const byDate = useMemo(() => eventsByDate(data?.events || [], month), [data, month]);
  const overtimeCount = (data?.events || []).filter((e) => e.type === 'OVERTIME').length;
  const selectedDate = selected.startsWith(month) ? selected : dates[0];

  const goMonth = (next) => {
    setMonth(next);
    setSelected(next === thisMonth() ? today : `${next}-01`);
  };

  const header = (
    <PageHeader
      title="캘린더"
      description="내 휴가·특근과 근태를 한 달 단위로 봅니다. 날짜를 누르면 그날의 기록과 신청 바로가기가 나옵니다."
    />
  );

  if (!data) {
    return (
      <>
        {header}
        <PageLoader />
      </>
    );
  }
  if (data.attendanceError && data.eventsError) {
    return (
      <>
        {header}
        <EmptyState icon={CalendarDays} title="캘린더를 불러오지 못했습니다" description={data.attendanceError} />
      </>
    );
  }

  const notice = !canAttendance
    ? '근태 메뉴 권한이 없어 휴가·특근 일정만 보입니다.'
    : data.attendanceError
      ? `근태 기록을 불러오지 못했습니다: ${data.attendanceError}`
      : data.eventsError
        ? `휴가·특근 일정을 불러오지 못했습니다: ${data.eventsError}`
        : '';

  return (
    <>
      {header}
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Card>
          <div className="flex flex-col gap-4 border-b border-gray-100 px-5 py-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  icon={ChevronLeft}
                  aria-label="이전 달"
                  onClick={() => goMonth(shiftMonth(month, -1))}
                />
                <h2 className="min-w-[110px] text-center text-base font-semibold text-gray-900">{monthLabel(month)}</h2>
                <Button
                  variant="ghost"
                  size="sm"
                  icon={ChevronRight}
                  aria-label="다음 달"
                  onClick={() => goMonth(shiftMonth(month, 1))}
                />
              </div>
              {month !== thisMonth() && (
                <Button variant="secondary" size="sm" onClick={() => goMonth(thisMonth())}>
                  이번 달
                </Button>
              )}
            </div>
            {data.attendance?.summary && (
              <MonthSummary summary={data.attendance.summary} overtimeCount={overtimeCount} />
            )}
            {notice && <p className="text-xs text-amber-700">{notice}</p>}
          </div>
          <div className="grid grid-cols-7 border-l border-gray-100 text-center text-xs font-medium text-gray-500">
            {WEEKDAYS.map((w, i) => (
              <div
                key={w}
                className={cn(
                  'border-b border-r border-gray-100 py-2',
                  i === 0 && 'text-red-500',
                  i === 6 && 'text-blue-500',
                )}
              >
                {w}
              </div>
            ))}
          </div>
          <div className="grid grid-cols-7 border-l border-gray-100">
            {Array.from({ length: weekdayOf(dates[0]) }, (_, i) => (
              <div key={`pad${i}`} className="min-h-[92px] border-b border-r border-gray-100 bg-gray-50/50" />
            ))}
            {dates.map((date) => (
              <DayCell
                key={date}
                date={date}
                day={dayMap.get(date)}
                events={byDate.get(date) || []}
                isToday={date === today}
                selected={date === selectedDate}
                onSelect={setSelected}
              />
            ))}
          </div>
          <Legend />
        </Card>

        <div className="space-y-4">
          <DayPanel
            date={selectedDate}
            day={dayMap.get(selectedDate)}
            events={byDate.get(selectedDate) || []}
            today={today}
            canDraft={canDraft}
          />
          <p className="px-1 text-xs text-gray-500">
            출퇴근은{' '}
            <Link to="/attendance" className="text-blue-600 hover:underline">
              내 근태
            </Link>
            , 휴가 잔여·내역은{' '}
            <Link to="/leaves" className="text-blue-600 hover:underline">
              내 연차
            </Link>
            에서 볼 수 있습니다.
          </p>
        </div>
      </div>
    </>
  );
}
