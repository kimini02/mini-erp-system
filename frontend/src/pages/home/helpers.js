export const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
export const md = (date) => `${Number(date.slice(5, 7))}.${date.slice(8, 10)}`;
export const todayStr = () => new Date().toLocaleDateString('sv-SE');

/** 오늘 09:14 / 어제 17:40 / 10.10 15:02 */
export function whenText(value) {
  if (!value) return '';
  const day = value.slice(0, 10);
  const time = value.slice(11, 16);
  const yesterday = new Date(Date.now() - 86400000).toLocaleDateString('sv-SE');
  if (day === todayStr()) return `오늘 ${time}`;
  if (day === yesterday) return `어제 ${time}`;
  return `${md(day)} ${time}`;
}

export const LEAVE_WORDS = { LEAVE: '연차', HALF_AM: '오전 반차', HALF_PM: '오후 반차' };
