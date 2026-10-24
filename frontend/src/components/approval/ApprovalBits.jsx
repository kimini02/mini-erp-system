import { cn } from '@/lib/cn';
import { DOC_STATUS_META } from '@/lib/approval';
import { Badge } from '@/components/ui';

export function DocStatusBadge({ status }) {
  const meta = DOC_STATUS_META[status] || { label: status || '-', tone: 'gray' };
  return <Badge tone={meta.tone} dot>{meta.label}</Badge>;
}

const DOT = {
  DONE: 'bg-blue-600 ring-blue-600',
  CURRENT: 'bg-white ring-2 ring-blue-600',
  TODO: 'bg-white ring-gray-300',
  REJECTED: 'bg-red-500 ring-red-500',
  WITHDRAWN: 'bg-amber-400 ring-amber-400',
  SKIPPED: 'bg-gray-100 ring-gray-200',
};
const STEP_TEXT = { DONE: '완료', CURRENT: '진행 중', TODO: '대기', REJECTED: '반려', WITHDRAWN: '회수', SKIPPED: '건너뜀' };

/** 결재 진행 점 (●─●─○). 첫 점은 기안 */
export function StepDots({ steps = [] }) {
  if (steps.length === 0) return <span className="text-xs text-gray-400">-</span>;
  const label = steps.map((s, i) => `${i === 0 ? '기안' : `${i}단계`} ${STEP_TEXT[s] || s}`).join(', ');
  return (
    <span className="inline-flex items-center" role="img" aria-label={label}>
      {steps.map((s, i) => (
        <span key={i} className="inline-flex items-center">
          {i > 0 && <span className={cn('h-px w-3', steps[i - 1] === 'DONE' ? 'bg-blue-600' : 'bg-gray-300')} />}
          <span className={cn('h-2 w-2 rounded-full ring-1 ring-inset', DOT[s] || DOT.TODO)} />
        </span>
      ))}
    </span>
  );
}

/** 이름 · 직급 · 부서 한 줄 */
export function PersonText({ person, className }) {
  if (!person) return <span className={cn('text-gray-400', className)}>-</span>;
  return (
    <span className={className}>
      <span className="font-medium text-gray-900">{person.name}</span>
      {person.title && <span className="text-gray-500"> {person.title}</span>}
      {person.department && <span className="text-gray-400"> · {person.department}</span>}
    </span>
  );
}
