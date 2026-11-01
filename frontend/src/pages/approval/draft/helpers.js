import { timeOf } from '@/lib/attendance';

export function fixProblem(body, record) {
  if (record && !record.clockInAt && !body.clockIn)
    return { field: 'in', message: '이날은 출근 기록이 없어 출근 시각을 꼭 적어야 합니다.' };
  const at = (hhmm, existing) => (hhmm ? `${body.workDate}T${hhmm}` : existing ? existing.slice(0, 16) : null);
  const inAt = at(body.clockIn, record?.clockInAt);
  const outAt = at(body.clockOut, record?.clockOutAt);
  if (inAt && outAt && outAt <= inAt) {
    return {
      field: 'out',
      message: body.clockOut
        ? '퇴근 시각은 출근 시각보다 늦어야 합니다.'
        : `지금 퇴근 기록(${timeOf(record.clockOutAt)})보다 늦은 출근 시각입니다. 퇴근 시각도 함께 고쳐 주세요.`,
    };
  }
  return null;
}
