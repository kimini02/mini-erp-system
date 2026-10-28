import { useState } from 'react';
import { departmentApi } from '@/api/org';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Button, Card, CardBody, CardFooter, CardHeader, Field, Input, Select } from '@/components/ui';

const addMinutes = (time, minutes) => {
  const [h, m] = time.split(':').map(Number);
  const total = h * 60 + m + Number(minutes || 0);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
};

/* ───────── 조직도 ───────── */

export default function WorkScheduleCard({ department, defaultSchedule, canUpdate, onSaved }) {
  const initial = department.workSchedule;
  const [useDefault, setUseDefault] = useState(initial.useDefault);
  const [form, setForm] = useState({
    startTime: initial.useDefault ? '09:00' : initial.startTime,
    endTime: initial.useDefault ? '18:00' : initial.endTime,
    lateGraceMin: initial.useDefault ? 10 : initial.lateGraceMin,
  });
  const [saving, setSaving] = useState(false);

  const effective = useDefault ? defaultSchedule : form;
  const invalid = !useDefault && form.endTime <= form.startTime;
  const dirty =
    useDefault !== initial.useDefault ||
    (!useDefault &&
      (form.startTime !== initial.startTime ||
        form.endTime !== initial.endTime ||
        Number(form.lateGraceMin) !== initial.lateGraceMin));

  const save = async () => {
    setSaving(true);
    try {
      await departmentApi.setWorkSchedule(
        department.id,
        useDefault ? { useDefault: true } : { useDefault: false, ...form, lateGraceMin: Number(form.lateGraceMin) },
      );
      toast.success('근무시간을 저장했습니다.');
      onSaved();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const option = (value, title, desc) => {
    const active = useDefault === value;
    return (
      <button
        type="button"
        role="radio"
        aria-checked={active}
        disabled={!canUpdate}
        onClick={() => setUseDefault(value)}
        className={cn(
          'flex-1 rounded-xl border px-4 py-3 text-left transition-colors disabled:cursor-default',
          active ? 'border-blue-500 bg-blue-50/60 ring-1 ring-blue-500' : 'border-gray-200 hover:border-gray-300',
        )}
      >
        <span className="block text-sm font-semibold text-gray-900">{title}</span>
        <span className="mt-0.5 block text-[13px] text-gray-500">{desc}</span>
      </button>
    );
  };

  return (
    <Card>
      <CardHeader
        title="근무시간"
        description="지각·조퇴 판정에 씁니다. 바꿔도 이미 찍힌 출근 기록의 판정은 바뀌지 않습니다."
      />
      <CardBody className="space-y-4">
        <div role="radiogroup" aria-label="근무시간 기준" className="flex flex-col gap-2 sm:flex-row">
          {option(
            true,
            '회사 기본 따르기',
            defaultSchedule
              ? `${defaultSchedule.startTime} ~ ${defaultSchedule.endTime} · 유예 ${defaultSchedule.lateGraceMin}분`
              : '불러오는 중',
          )}
          {option(false, '이 부서만 따로 정하기', '시차 출근, 교대 근무 부서')}
        </div>

        {!useDefault && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Field label="출근" htmlFor="ws-start">
              <Input
                id="ws-start"
                type="time"
                value={form.startTime}
                disabled={!canUpdate}
                onChange={(e) => setForm((f) => ({ ...f, startTime: e.target.value }))}
              />
            </Field>
            <Field label="퇴근" htmlFor="ws-end" error={invalid ? '퇴근은 출근보다 늦어야 합니다.' : undefined}>
              <Input
                id="ws-end"
                type="time"
                value={form.endTime}
                invalid={invalid}
                disabled={!canUpdate}
                onChange={(e) => setForm((f) => ({ ...f, endTime: e.target.value }))}
              />
            </Field>
            <Field label="지각 유예" htmlFor="ws-grace">
              <Select
                id="ws-grace"
                value={form.lateGraceMin}
                disabled={!canUpdate}
                onChange={(e) => setForm((f) => ({ ...f, lateGraceMin: Number(e.target.value) }))}
              >
                {[0, 5, 10, 15, 20, 30].map((m) => (
                  <option key={m} value={m}>
                    {m === 0 ? '없음' : `${m}분`}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        )}

        {effective && (
          <p className="rounded-lg bg-gray-50 px-3 py-2.5 text-[13px] text-gray-600">
            판정 예시:{' '}
            <b className="font-semibold text-gray-900">{addMinutes(effective.startTime, effective.lateGraceMin)}</b>{' '}
            이후 출근하면 지각, <b className="font-semibold text-gray-900">{effective.endTime}</b> 전에 퇴근하면 조퇴
          </p>
        )}
      </CardBody>
      {canUpdate && (
        <CardFooter>
          <Button onClick={save} loading={saving} disabled={!dirty || invalid}>
            근무시간 저장
          </Button>
        </CardFooter>
      )}
    </Card>
  );
}

/* ───────── 모달 ───────── */
