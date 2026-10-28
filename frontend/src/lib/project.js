// 프로젝트 · 업무 표시용 이름·색 (backend/md/15-rest-api-v2-m4.md, 시안 Kanban)

export const TASK_STATUSES = ['TODO', 'DOING', 'DONE'];

export const TASK_STATUS_META = {
  TODO: { label: '대기', dot: 'bg-gray-400', column: 'bg-gray-50', chip: 'bg-gray-100 text-gray-700', empty: '대기 중인 업무가 없습니다.' },
  DOING: { label: '진행 중', dot: 'bg-blue-500', column: 'bg-blue-50/40', chip: 'bg-blue-50 text-blue-700', empty: '진행 중인 업무가 없습니다.' },
  DONE: { label: '완료', dot: 'bg-emerald-500', column: 'bg-emerald-50/40', chip: 'bg-emerald-50 text-emerald-700', empty: '완료한 업무가 없습니다.' },
};

export const PROJECT_STATUS_META = {
  READY: { label: '준비', tone: 'gray' },
  PROGRESS: { label: '진행 중', tone: 'blue' },
  DONE: { label: '완료', tone: 'green' },
};

export const PRIORITY_META = {
  HIGH: { label: '높음', chip: 'bg-red-50 text-red-700' },
  MEDIUM: { label: '중간', chip: 'bg-amber-50 text-amber-700' },
  LOW: { label: '낮음', chip: 'bg-gray-100 text-gray-600' },
};
export const PRIORITIES = ['HIGH', 'MEDIUM', 'LOW'];

/** 2026.09.01 ~ 2026.12.31 */
export const formatPeriod = (p) => `${p.startDate.replaceAll('-', '.')} ~ ${p.endDate.replaceAll('-', '.')}`;

/** 사람마다 늘 같은 색 (시안의 원형 머리글자) */
const AVATAR_COLORS = [
  'bg-blue-100 text-blue-700', 'bg-amber-100 text-amber-800', 'bg-emerald-100 text-emerald-800', 'bg-violet-100 text-violet-800',
  'bg-pink-100 text-pink-800', 'bg-orange-100 text-orange-800', 'bg-sky-100 text-sky-800', 'bg-teal-100 text-teal-800',
];
export const avatarColor = (id) => AVATAR_COLORS[Math.abs(Number(id) || 0) % AVATAR_COLORS.length];
/** 이름 뒤 두 글자 (박지훈 → 지훈) */
export const shortName = (name = '') => (name.length > 2 ? name.slice(-2) : name);

/** 마감 표시: 완료면 '10/02 완료', 지났으면 'n일 지남', 아니면 '10/16 마감' */
export function dueText(task) {
  const md = task.endDate ? `${task.endDate.slice(5, 7)}/${task.endDate.slice(8, 10)}` : '';
  if (task.status === 'DONE') return { text: `${md} 완료`, late: false };
  if (task.overdueDays > 0) return { text: `${task.overdueDays}일 지남`, late: true };
  return { text: `${md} 마감`, late: false };
}

/** 받침에 맞는 목적격 조사 (마감일 → 을, 제목 → 을, 우선순위 → 를) */
function objectParticle(word = '') {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return '을(를)';
  return code % 28 === 0 ? '를' : '을';
}

/** 방향 조사 (완료 → 로, 진행 중 → 으로, 대기 → 로) */
export function toParticle(word = '') {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return '(으)로';
  const final = code % 28;
  return final === 0 || final === 8 ? '로' : '으로';
}

/** 변경 이력 한 줄 (명세 4.5) */
export function historySentence(h) {
  const who = h.actor?.name || '시스템';
  const status = (code) => TASK_STATUS_META[code]?.label || code;
  switch (h.action) {
    case 'CREATED': return `${who}님이 업무를 만들었습니다`;
    case 'STATUS': return `${who}님이 상태를 ${status(h.from)} → ${status(h.to)}${toParticle(status(h.to))} 바꿨습니다`;
    case 'MOVED': return `${who}님이 순서를 바꿨습니다`;
    case 'ASSIGNED': return `${who}님이 ${h.target?.name || '담당자'}님을 담당자로 지정했습니다`;
    case 'UNASSIGNED': return `${who}님이 ${h.target?.name || '담당자'}님을 담당에서 뺐습니다`;
    case 'UPDATED': return `${who}님이 ${h.to || '내용'}${objectParticle(h.to || '내용')} 고쳤습니다`;
    case 'OVERDUE_NOTIFIED': return '마감일이 지나 담당자에게 알렸습니다';
    default: return `${who} · ${h.action}`;
  }
}
