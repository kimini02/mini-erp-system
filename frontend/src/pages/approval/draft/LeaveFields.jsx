import { LEAVE_TYPE_OPTIONS, formatDays, isHalfDay } from '@/lib/approval';
import { cn } from '@/lib/cn';
import { Field, Input, Select, Textarea } from '@/components/ui';

export default function LeaveFields({ body, set, handoverOptions, days, daysError }) {
  const half = isHalfDay(body.leaveType);
  return (
    <div className="space-y-4">
      <Field label="휴가 종류" required>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          {LEAVE_TYPE_OPTIONS.map((t) => {
            const active = t.value === body.leaveType;
            return (
              <button
                key={t.value}
                type="button"
                onClick={() => set('leaveType', t.value)}
                className={cn(
                  'rounded-lg border px-3 py-2 text-left transition-colors',
                  active ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600' : 'border-gray-200 hover:border-gray-300',
                )}
              >
                <span className={cn('block text-[13px] font-medium', active ? 'text-blue-700' : 'text-gray-900')}>
                  {t.label}
                </span>
                <span className="block text-xs text-gray-500">{t.hint}</span>
              </button>
            );
          })}
        </div>
      </Field>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label={half ? '날짜' : '시작일'} htmlFor="lv-start" required>
          <Input id="lv-start" type="date" value={body.startDate} onChange={(e) => set('startDate', e.target.value)} />
        </Field>
        {!half && (
          <Field
            label="종료일"
            htmlFor="lv-end"
            required
            error={
              body.endDate && body.startDate && body.endDate < body.startDate
                ? '종료일이 시작일보다 빠릅니다.'
                : undefined
            }
          >
            <Input
              id="lv-end"
              type="date"
              min={body.startDate || undefined}
              value={body.endDate}
              onChange={(e) => set('endDate', e.target.value)}
            />
          </Field>
        )}
      </div>

      {(days || daysError) && (
        <div
          className={cn(
            'rounded-lg px-3.5 py-3 text-[13px]',
            daysError ? 'bg-red-50 text-red-700' : 'bg-gray-50 text-gray-700',
          )}
        >
          {daysError ||
            (body.leaveType === 'SICK' || body.leaveType === 'FAMILY' ? (
              '병가·경조 휴가는 연차에서 빼지 않습니다.'
            ) : (
              <>
                연차 <b className="font-semibold text-gray-900">{formatDays(days.days)}</b> 차감
                {days.offDays > 0 && ` · 주말·공휴일 ${days.offDays}일 제외`}
              </>
            ))}
        </div>
      )}

      <Field label="업무 인수자" htmlFor="lv-handover">
        <Select id="lv-handover" value={body.handoverUserId} onChange={(e) => set('handoverUserId', e.target.value)}>
          <option value="">지정 안 함</option>
          {handoverOptions.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} {p.title} · {p.department}
            </option>
          ))}
        </Select>
      </Field>

      <Field label="사유" htmlFor="lv-reason" hint="같은 날짜에 이미 신청한 휴가가 있으면 상신할 수 없습니다.">
        <Textarea id="lv-reason" rows={3} value={body.reason} onChange={(e) => set('reason', e.target.value)} />
      </Field>
    </div>
  );
}
