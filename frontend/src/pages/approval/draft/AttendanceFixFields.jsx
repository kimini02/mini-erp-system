import { useEffect, useState } from 'react';
import { attendanceApi } from '@/api/attendance';
import { CELL_META, dayCode, shiftDate, timeOf, todayLocal } from '@/lib/attendance';
import { Badge, Field, Input, Textarea } from '@/components/ui';
import { fixProblem } from './helpers';

/** 근태 정정: 정정할 날의 지금 기록을 보여 주고, 바꿀 시각만 입력 (M3 명세 5장) */
export default function AttendanceFixFields({ body, set, onRecord }) {
  const [current, setCurrent] = useState(null);
  const today = todayLocal();
  const min = shiftDate(today, -30);
  const max = shiftDate(today, -1);
  const validDate = body.workDate && body.workDate >= min && body.workDate <= max;

  useEffect(() => {
    if (!validDate) {
      setCurrent(null);
      return undefined;
    }
    let alive = true;
    attendanceApi
      .me(body.workDate.slice(0, 7))
      .then((r) => alive && setCurrent(r.days.find((d) => d.date === body.workDate) || null))
      .catch(() => alive && setCurrent(null));
    return () => {
      alive = false;
    };
  }, [body.workDate, validDate]);

  useEffect(() => {
    onRecord?.(current);
  }, [current, onRecord]);

  const meta = current ? CELL_META[dayCode(current)] || CELL_META.NONE : null;
  const needIn = Boolean(current && !current.clockInAt);
  const problem = fixProblem(body, current);
  return (
    <div className="space-y-4">
      <Field
        label="정정할 날짜"
        htmlFor="fix-date"
        required
        hint="최근 30일 안의 지난 날짜만 고칠 수 있습니다."
        error={body.workDate && !validDate ? '최근 30일 안의 지난 날짜를 골라 주세요.' : undefined}
      >
        <Input
          id="fix-date"
          type="date"
          min={min}
          max={max}
          value={body.workDate}
          onChange={(e) => set('workDate', e.target.value)}
        />
      </Field>
      {current && (
        <div className="rounded-lg bg-gray-50 px-3.5 py-3 text-[13px] text-gray-700">
          지금 기록: 출근 <b className="font-semibold text-gray-900">{timeOf(current.clockInAt) || '없음'}</b>
          {' · '}퇴근 <b className="font-semibold text-gray-900">{timeOf(current.clockOutAt) || '없음'}</b>
          {meta?.label && (
            <>
              {' '}
              · <Badge tone={meta.tone}>{meta.label}</Badge>
            </>
          )}
          {current.fixDocument && (
            <span className="ml-1 text-xs text-gray-500">(이미 {current.fixDocument.docNo}로 정정됨)</span>
          )}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field
          label="바른 출근 시각"
          htmlFor="fix-in"
          required={needIn}
          hint={needIn ? '이날은 출근 기록이 없어 출근 시각을 꼭 적어야 합니다.' : '바꾸지 않을 시각은 비워 두세요.'}
          error={problem?.field === 'in' && body.clockOut ? problem.message : undefined}
        >
          <Input id="fix-in" type="time" value={body.clockIn} onChange={(e) => set('clockIn', e.target.value)} />
        </Field>
        <Field label="바른 퇴근 시각" htmlFor="fix-out" error={problem?.field === 'out' ? problem.message : undefined}>
          <Input
            id="fix-out"
            type="time"
            value={body.clockOut}
            onChange={(e) => set('clockOut', e.target.value)}
            invalid={problem?.field === 'out'}
          />
        </Field>
      </div>
      <Field label="사유" htmlFor="fix-reason" required hint="예: 출입카드 오류로 출근을 늦게 찍었습니다.">
        <Textarea id="fix-reason" rows={3} value={body.reason} onChange={(e) => set('reason', e.target.value)} />
      </Field>
    </div>
  );
}

/* ───────── 양식별 입력 칸 ───────── */
