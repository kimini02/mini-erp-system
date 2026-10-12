import { cn } from '@/lib/cn';
import { STATUS_META } from '@/lib/labels';

const tones = {
  gray: 'bg-gray-100 text-gray-700 ring-gray-500/15',
  blue: 'bg-blue-50 text-blue-700 ring-blue-600/15',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/15',
  amber: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  red: 'bg-red-50 text-red-700 ring-red-600/15',
};

const dots = {
  gray: 'bg-gray-400',
  blue: 'bg-blue-500',
  green: 'bg-emerald-500',
  amber: 'bg-amber-500',
  red: 'bg-red-500',
};

export function Badge({ tone = 'gray', dot = false, className, children }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
        tones[tone],
        className
      )}
    >
      {dot && <span className={cn('h-1.5 w-1.5 rounded-full', dots[tone])} />}
      {children}
    </span>
  );
}

/** 결재 상태 배지 (PENDING / APPROVED / REJECTED) */
export function StatusBadge({ status }) {
  const meta = STATUS_META[status] || { tone: 'gray', label: status || '-' };
  return (
    <Badge tone={meta.tone} dot>
      {meta.label}
    </Badge>
  );
}
