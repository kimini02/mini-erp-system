import { cn } from '@/lib/cn';

export function LogoMark({ inverse = false, className }) {
  return (
    <svg viewBox="0 0 32 32" className={cn('h-7 w-7 shrink-0', className)} aria-hidden="true">
      <rect width="32" height="32" rx="7" fill={inverse ? '#fff' : '#2563eb'} />
      <path
        d="M8 10l3.2 12L16 12.5 20.8 22 24 10"
        fill="none"
        stroke={inverse ? '#2563eb' : '#fff'}
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export default function Logo({ inverse = false, className }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <LogoMark inverse={inverse} />
      <span className={cn('text-[15px] font-semibold tracking-tight', inverse ? 'text-white' : 'text-gray-900')}>
        WorkFlow
      </span>
    </span>
  );
}
