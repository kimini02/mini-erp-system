import { hoursBetween } from '@/lib/approval';
import { Field, Input, Textarea } from '@/components/ui';

const WEEKEND = new Set([0, 6]);

export default function OvertimeFields({ body, set }) {
  const day = body.overtimeDate ? new Date(`${body.overtimeDate}T00:00:00`).getDay() : null;
  const hours = hoursBetween(body.startTime, body.endTime);
  const invalidTime = body.startTime && body.endTime && body.endTime <= body.startTime;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Field label="근무일" htmlFor="ot-date" required>
          <Input
            id="ot-date"
            type="date"
            value={body.overtimeDate}
            onChange={(e) => set('overtimeDate', e.target.value)}
          />
        </Field>
        <Field label="시작" htmlFor="ot-start" required>
          <Input id="ot-start" type="time" value={body.startTime} onChange={(e) => set('startTime', e.target.value)} />
        </Field>
        <Field
          label="종료"
          htmlFor="ot-end"
          required
          error={invalidTime ? '종료 시각은 시작 시각보다 늦어야 합니다.' : undefined}
        >
          <Input
            id="ot-end"
            type="time"
            value={body.endTime}
            onChange={(e) => set('endTime', e.target.value)}
            invalid={invalidTime}
          />
        </Field>
      </div>
      {hours && !invalidTime && (
        <p className="rounded-lg bg-gray-50 px-3.5 py-3 text-[13px] text-gray-700">
          <b className="font-semibold text-gray-900">{hours}</b>
          {day !== null && ` · ${WEEKEND.has(day) ? '주말 근무' : '평일 연장 근무'}`}
        </p>
      )}
      <Field label="업무 내용" htmlFor="ot-reason" required>
        <Textarea id="ot-reason" rows={3} value={body.reason} onChange={(e) => set('reason', e.target.value)} />
      </Field>
    </div>
  );
}
