import { useState } from 'react';
import { Link } from 'react-router-dom';
import { attendanceApi } from '@/api/attendance';
import { formatDays } from '@/lib/approval';
import { IN_RESULT_LABELS, OUT_RESULT_LABELS, timeOf } from '@/lib/attendance';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Badge, Button } from '@/components/ui';

function todayStatus(today) {
  if (today.clockOutAt) return { label: '퇴근', tone: 'gray' };
  if (today.clockInAt) return { label: '근무 중', tone: 'green' };
  if (today.dayType === 'HOLIDAY') return { label: today.holidayName || '휴일', tone: 'gray' };
  if (today.leave) return { label: '휴가', tone: 'blue' };
  return { label: '출근 전', tone: 'amber' };
}

export default function ProfileCard({ user, initialToday, leave, tiles }) {
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
