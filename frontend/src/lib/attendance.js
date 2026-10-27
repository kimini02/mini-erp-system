// 근태 표시용 이름·색 (backend/md/14-rest-api-v2-m3.md 1·3장)

/** 근태표 칸 코드: 서버가 하나로 정해 주고, 화면은 색과 글자만 정합니다. */
export const CELL_META = {
  ON_TIME: { label: '정상', short: '○', className: 'bg-emerald-50 text-emerald-700', tone: 'green' },
  LATE: { label: '지각', short: '지', className: 'bg-amber-100 text-amber-800', tone: 'amber' },
  EARLY: { label: '조퇴', short: '조', className: 'bg-orange-100 text-orange-800', tone: 'amber' },
  MISSING: { label: '퇴근 누락', short: '누', className: 'bg-violet-100 text-violet-700', tone: 'amber' },
  ABSENT: { label: '결근', short: '결', className: 'bg-red-100 text-red-700', tone: 'red' },
  LEAVE: { label: '휴가', short: '휴', className: 'bg-blue-100 text-blue-700', tone: 'blue' },
  HALF_AM: { label: '오전 반차', short: '반', className: 'bg-blue-50 text-blue-700', tone: 'blue' },
  HALF_PM: { label: '오후 반차', short: '반', className: 'bg-blue-50 text-blue-700', tone: 'blue' },
  HOLIDAY_WORK: { label: '휴일 근무', short: '근', className: 'bg-indigo-50 text-indigo-700', tone: 'blue' },
  HOLIDAY: { label: '휴일', short: '', className: 'bg-gray-50', tone: 'gray' },
  FUTURE: { label: '', short: '', className: 'bg-white', tone: 'gray' },
  NONE: { label: '기록 없음', short: '', className: 'bg-white', tone: 'gray' },
};

/** 범례에 보여 줄 순서 */
export const LEGEND_CODES = ['ON_TIME', 'LATE', 'EARLY', 'MISSING', 'ABSENT', 'LEAVE', 'HALF_AM', 'HOLIDAY_WORK', 'HOLIDAY'];

/** 정정 신청을 권할 만한 칸 */
export const FIXABLE_CODES = new Set(['LATE', 'EARLY', 'MISSING', 'ABSENT']);

/**
 * 내 근태 하루(`GET /attendance/me`의 days 항목)를 칸 코드 하나로 (명세 3.1 우선순위)
 * 월간 근태표는 서버가 code를 주지만, 내 근태는 원래 값(dayType·inResult·outResult·absent)만 줍니다.
 */
export function dayCode(day, today = todayLocal()) {
  if (day.code) return day.code;
  if (day.absent) return 'ABSENT';
  if (day.dayType === 'LEAVE') return 'LEAVE';
  if (day.outResult === 'MISSING') return 'MISSING';
  if (day.inResult === 'LATE') return 'LATE';
  if (day.outResult === 'EARLY') return 'EARLY';
  if (day.dayType === 'HALF_AM' || day.dayType === 'HALF_PM') return day.dayType;
  if (day.inResult === 'ON_TIME') return 'ON_TIME';
  if (day.dayType === 'HOLIDAY') return day.clockInAt ? 'HOLIDAY_WORK' : 'HOLIDAY';
  return day.date > today ? 'FUTURE' : 'NONE';
}

export const DAY_TYPE_LABELS = {
  WORKDAY: '근무일',
  HOLIDAY: '휴일',
  LEAVE: '휴가',
  HALF_AM: '오전 반차',
  HALF_PM: '오후 반차',
};

export const IN_RESULT_LABELS = { ON_TIME: '정상 출근', LATE: '지각', NONE: '판정 없음' };
export const OUT_RESULT_LABELS = { ON_TIME: '정상 퇴근', EARLY: '조퇴', MISSING: '퇴근 누락', NONE: '판정 없음' };

export const WEEKDAY_SHORT = { SUN: '일', MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토' };

/** '2026-10-12T09:03:12' → '09:03' */
export const timeOf = (dateTime) => (dateTime ? String(dateTime).slice(11, 16) : null);

export const todayLocal = () => new Date().toLocaleDateString('sv-SE');
export const thisMonth = () => todayLocal().slice(0, 7);

/** '2026-10' ± n개월 */
export function shiftMonth(month, n) {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, m - 1 + n, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export const monthLabel = (month) => `${month.slice(0, 4)}년 ${Number(month.slice(5, 7))}월`;

/** 날짜 문자열 ± n일 */
export function shiftDate(date, n) {
  const d = new Date(`${date}T00:00:00`);
  d.setDate(d.getDate() + n);
  return d.toLocaleDateString('sv-SE');
}
