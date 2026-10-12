import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/cn';

/** 페이지 상단 제목 영역 */
export function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-[22px] font-semibold tracking-tight text-gray-900">{title}</h1>
        {description && <p className="mt-1 text-sm text-gray-500">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

const iconTones = {
  gray: 'bg-gray-100 text-gray-600',
  blue: 'bg-blue-50 text-blue-600',
  green: 'bg-emerald-50 text-emerald-600',
  amber: 'bg-amber-50 text-amber-600',
  red: 'bg-red-50 text-red-600',
};

/** 숫자 요약 카드 */
export function StatCard({ label, value, unit, hint, icon: Icon, tone = 'gray', className }) {
  return (
    <div className={cn('rounded-xl border border-gray-200 bg-white p-5', className)}>
      <div className="flex items-center justify-between">
        <p className="text-[13px] font-medium text-gray-500">{label}</p>
        {Icon && (
          <span className={cn('flex h-8 w-8 items-center justify-center rounded-lg', iconTones[tone])}>
            <Icon size={16} />
          </span>
        )}
      </div>
      <p className="mt-2 text-2xl font-semibold tabular-nums tracking-tight text-gray-900">
        {value}
        {unit && <span className="ml-0.5 text-base font-medium text-gray-500">{unit}</span>}
      </p>
      {hint && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
    </div>
  );
}

export function EmptyState({ icon: Icon, title, description, action, className }) {
  return (
    <div className={cn('flex flex-col items-center justify-center px-6 py-14 text-center', className)}>
      {Icon && (
        <span className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-gray-100 text-gray-400">
          <Icon size={20} />
        </span>
      )}
      <p className="text-sm font-medium text-gray-700">{title}</p>
      {description && <p className="mt-1 text-[13px] text-gray-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Avatar({ name, size = 'md', className }) {
  const sizes = { sm: 'h-7 w-7 text-xs', md: 'h-8 w-8 text-[13px]', lg: 'h-10 w-10 text-sm', xl: 'h-16 w-16 text-xl' };
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full bg-blue-100 font-semibold text-blue-700',
        sizes[size],
        className
      )}
    >
      {name?.trim()?.charAt(0) || '?'}
    </span>
  );
}

const barTones = { blue: 'bg-blue-600', green: 'bg-emerald-500', amber: 'bg-amber-500', red: 'bg-red-500', gray: 'bg-gray-400' };

export function Progress({ value = 0, tone = 'blue', className }) {
  const pct = Math.max(0, Math.min(100, Number(value) || 0));
  return (
    <div className={cn('h-1.5 w-full overflow-hidden rounded-full bg-gray-100', className)}>
      <div className={cn('h-full rounded-full transition-[width] duration-300', barTones[tone])} style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Spinner({ size = 18, className }) {
  return <Loader2 size={size} className={cn('animate-spin text-gray-400', className)} />;
}

/** 페이지/카드 단위 로딩 표시 */
export function PageLoader({ label = '불러오는 중…', className }) {
  return (
    <div className={cn('flex items-center justify-center gap-2 py-24 text-sm text-gray-500', className)}>
      <Spinner />
      {label}
    </div>
  );
}

/** 정보 행: 라벨 - 값 (프로필, 상세 정보용) */
export function InfoRow({ label, value }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <dt className="text-[13px] text-gray-500">{label}</dt>
      <dd className="text-sm font-medium text-gray-900">{value}</dd>
    </div>
  );
}
