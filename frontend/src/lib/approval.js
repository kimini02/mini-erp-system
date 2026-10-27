// 전자결재 · 연차 표시용 이름과 형식 (backend/md/13-rest-api-v2-m2.md)

export const DOC_STATUS_META = {
  DRAFT: { label: '임시 저장', tone: 'gray' },
  IN_PROGRESS: { label: '진행 중', tone: 'blue' },
  APPROVED: { label: '승인', tone: 'green' },
  REJECTED: { label: '반려', tone: 'red' },
  WITHDRAWN: { label: '회수', tone: 'amber' },
};

export const LINE_STATUS_META = {
  PENDING: { label: '결재 차례', tone: 'blue' },
  WAITING: { label: '대기', tone: 'gray' },
  APPROVED: { label: '승인', tone: 'green' },
  REJECTED: { label: '반려', tone: 'red' },
  SKIPPED: { label: '건너뜀', tone: 'gray' },
};

export const SKIP_REASON_LABELS = {
  SELF: '기안자 본인이라 건너뜀',
  DUPLICATE: '앞 단계와 같은 사람이라 건너뜀',
};

/** 결재함 탭 (명세 4장) */
export const INBOX_BOXES = [
  { key: 'todo', label: '결재 대기' },
  { key: 'in-progress', label: '결재 진행' },
  { key: 'done', label: '결재 완료' },
  { key: 'mine', label: '내 기안' },
  { key: 'drafts', label: '임시 보관' },
  { key: 'reference', label: '참조' },
];

export const LEAVE_TYPE_OPTIONS = [
  { value: 'ANNUAL', label: '연차', hint: '근무일 수만큼' },
  { value: 'HALF_AM', label: '오전 반차', hint: '0.5일' },
  { value: 'HALF_PM', label: '오후 반차', hint: '0.5일' },
  { value: 'SICK', label: '병가', hint: '연차에서 빼지 않음' },
  { value: 'FAMILY', label: '경조 휴가', hint: '연차에서 빼지 않음' },
];
export const LEAVE_TYPE_NAMES = Object.fromEntries(LEAVE_TYPE_OPTIONS.map((o) => [o.value, o.label]));
export const isHalfDay = (type) => type === 'HALF_AM' || type === 'HALF_PM';

export const LEDGER_TYPE_META = {
  GRANT: { label: '부여', tone: 'green' },
  USE: { label: '사용', tone: 'red' },
  RESERVE: { label: '예약', tone: 'amber' },
  RELEASE: { label: '예약 해제', tone: 'blue' },
  CANCEL: { label: '취소', tone: 'gray' },
  ADJUST: { label: '조정', tone: 'gray' },
};

// 알림 title에는 문서 제목이 옵니다. 화면은 종류에 맞춰 문장으로 보여 줍니다.
export const NOTIFICATION_TYPE_META = {
  APPROVAL_REQUESTED: { label: '결재 요청', dot: 'bg-blue-500', sentence: (t) => `'${t}' 결재 차례입니다.` },
  APPROVED: { label: '승인', dot: 'bg-emerald-500', sentence: (t) => `'${t}' 문서가 최종 승인되었습니다.` },
  REJECTED: { label: '반려', dot: 'bg-red-500', sentence: (t) => `'${t}' 문서가 반려되었습니다.` },
  WITHDRAWN: { label: '회수', dot: 'bg-amber-500', sentence: (t) => `'${t}' 문서가 회수되었습니다.` },
  REFERENCED: { label: '참조', dot: 'bg-gray-400', sentence: (t) => `'${t}' 문서에 참조로 지정되었습니다.` },
};

export const HISTORY_ACTION_LABELS = {
  SUBMIT: '상신',
  LINE_GENERATED: '결재선 생성',
  APPROVE: '승인',
  REJECT: '반려',
  WITHDRAW: '회수',
  AUTO_APPROVE: '자동 승인',
};

export const APPROVER_TYPE_OPTIONS = [
  { value: 'DEPT_HEAD', label: '기안자의 부서장' },
  { value: 'UPPER_DEPT_HEAD', label: '기안자의 상위 부서장' },
  { value: 'ROLE', label: '역할을 가진 사람' },
  { value: 'USER', label: '특정 사원' },
];

export const FORM_OPTION_META = [
  { key: 'fallbackToUpper', label: '부서장 공석이면 위로', desc: '부서장이 비어 있으면 더 위 부서장이 결재합니다. 끝까지 없으면 상신할 수 없습니다.' },
  { key: 'skipSelf', label: '기안자 본인은 건너뛰기', desc: '결재자가 기안자 본인이면 그 단계를 건너뜁니다.' },
  { key: 'dedupeApprover', label: '같은 사람은 한 번만', desc: '앞 단계와 같은 사람이 다시 나오면 한 번만 결재합니다.' },
  { key: 'allowLineEdit', label: '기안자가 결재선 수정', desc: '기안할 때 결재자를 추가하거나 순서를 바꿀 수 있습니다.' },
];

const toDate = (value) => (value ? String(value).replace('T', ' ') : '');

/** '2026-10-12T09:14:00' → '2026.10.12 09:14' */
export function formatDateTime(value) {
  const s = toDate(value);
  return s ? `${s.slice(0, 10).replaceAll('-', '.')} ${s.slice(11, 16)}`.trim() : '-';
}

/** '2026-10-12' 또는 날짜시각 → '2026.10.12' */
export function formatDate(value) {
  return value ? String(value).slice(0, 10).replaceAll('-', '.') : '-';
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
/** '2026-10-19' → '10.19 (월)' */
export function formatDay(value) {
  if (!value) return '-';
  const d = new Date(`${value}T00:00:00`);
  return `${value.slice(5, 7)}.${value.slice(8, 10)} (${WEEKDAYS[d.getDay()]})`;
}

/** 2 → '2일', 0.5 → '0.5일' */
export function formatDays(days) {
  if (days === null || days === undefined) return '-';
  const n = Number(days);
  return `${Number.isInteger(n) ? n : n.toFixed(1)}일`;
}

export function formatSignedDays(days) {
  const n = Number(days);
  return `${n > 0 ? '+' : n < 0 ? '−' : ''}${formatDays(Math.abs(n)).replace('일', '')}`;
}

export function formatAmount(amount) {
  return amount || amount === 0 ? `${Number(amount).toLocaleString('ko-KR')}원` : '-';
}

/** 기안 화면 제목 자동 완성: '연차 · 10/19~10/20' */
export function leaveTitle(type, start, end) {
  const md = (v) => `${Number(v.slice(5, 7))}/${Number(v.slice(8, 10))}`;
  if (!start) return LEAVE_TYPE_NAMES[type] || '휴가';
  return `${LEAVE_TYPE_NAMES[type] || '휴가'} · ${md(start)}${end && end !== start ? `~${md(end)}` : ''}`;
}

export function overtimeTitle(date) {
  if (!date) return '특근';
  const d = new Date(`${date}T00:00:00`);
  return `특근 · ${Number(date.slice(5, 7))}/${Number(date.slice(8, 10))} (${WEEKDAYS[d.getDay()]})`;
}

/** 'HH:mm' 두 개 사이 시간 (예: 6시간 30분) */
export function hoursBetween(start, end) {
  if (!start || !end) return '';
  const [sh, sm] = start.split(':').map(Number);
  const [eh, em] = end.split(':').map(Number);
  const minutes = eh * 60 + em - (sh * 60 + sm);
  if (minutes <= 0) return '';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h ? `${h}시간` : ''}${h && m ? ' ' : ''}${m ? `${m}분` : ''}`;
}
