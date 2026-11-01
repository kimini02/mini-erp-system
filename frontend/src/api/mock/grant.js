/**
 * 연차 자동 부여 규칙 (backend/md/14-rest-api-v2-m3.md 4.1)
 * M2 처음 데이터와 M3 부여 배치가 같은 계산을 쓰도록 따로 둡니다.
 */

const pad = (n) => String(n).padStart(2, '0');
const daysBetween = (a, b) => Math.round((new Date(`${b}T00:00:00Z`) - new Date(`${a}T00:00:00Z`)) / 86400000);
const ceilHalf = (x) => Math.ceil(x * 2) / 2;

/** 입사일에서 k개월 뒤 (그달에 같은 날이 없으면 그달 마지막 날) */
export const addMonthsClamp = (date, k) => {
  const [y, m, d] = date.split('-').map(Number);
  const target = new Date(Date.UTC(y, m - 1 + k, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  return `${target.getUTCFullYear()}-${pad(target.getUTCMonth() + 1)}-${pad(Math.min(d, last))}`;
};

/** on 날짜 기준 만 근속연수 */
const completedYears = (hireDate, on) => {
  const [hy, hm, hd] = hireDate.split('-').map(Number);
  const [y, m, d] = on.split('-').map(Number);
  return y - hy - (m < hm || (m === hm && d < hd) ? 1 : 0);
};

/**
 * date 가 속한 해에 date 까지 받아야 할 부여 목록 [{ key, date, days, memo }]
 * 퇴사일이 있으면 퇴사일 전 부여만 셉니다.
 */
export function grantsDue(user, date) {
  if (!user.hireDate) return [];
  const year = date.slice(0, 4);
  const jan1 = `${year}-01-01`;
  const anniversary = addMonthsClamp(user.hireDate, 12);
  const due = [];
  for (let k = 1; k <= 11; k += 1) {
    const d = addMonthsClamp(user.hireDate, k);
    if (d <= date && d < anniversary && d.slice(0, 4) === year)
      due.push({ key: `M-${user.id}-${k}`, date: d, days: 1, memo: `입사 ${k}개월 부여` });
  }
  if (anniversary <= date && anniversary.slice(0, 4) === year) {
    const left = daysBetween(anniversary, `${year}-12-31`) + 1;
    const total = daysBetween(jan1, `${year}-12-31`) + 1;
    due.push({
      key: `A-${user.id}`,
      date: anniversary,
      days: ceilHalf((15 * left) / total),
      memo: '입사 1주년 비례 부여',
    });
  }
  if (jan1 <= date && anniversary < jan1) {
    const years = completedYears(user.hireDate, jan1);
    due.push({
      key: `Y-${user.id}-${year}`,
      date: jan1,
      days: Math.min(25, 15 + Math.floor((years - 1) / 2)),
      memo: `${year}년 연차 부여 (근속 ${years}년)`,
    });
  }
  return due.filter((g) => !user.retiredDate || g.date < user.retiredDate);
}
