import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/cn';

const variants = {
  primary: 'bg-blue-600 text-white hover:bg-blue-700 disabled:bg-blue-300',
  secondary:
    'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50 hover:text-gray-900 disabled:text-gray-400 disabled:bg-white',
  ghost: 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 disabled:text-gray-300 disabled:bg-transparent',
  danger: 'bg-red-600 text-white hover:bg-red-700 disabled:bg-red-300',
  'danger-outline':
    'bg-white text-red-600 border border-gray-300 hover:bg-red-50 hover:border-red-200 disabled:text-red-300',
};

const sizes = {
  sm: 'h-8 px-3 text-[13px] gap-1.5',
  md: 'h-9 px-3.5 text-sm gap-2',
  lg: 'h-11 px-4 text-sm gap-2',
};

/**
 * 공통 버튼
 * variant: primary | secondary | ghost | danger | danger-outline
 * size: sm | md | lg
 */
export default function Button({
  variant = 'primary',
  size = 'md',
  icon: Icon,
  loading = false,
  block = false,
  type = 'button',
  className,
  children,
  disabled,
  ...props
}) {
  const iconSize = size === 'sm' ? 14 : 16;

  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-lg font-medium whitespace-nowrap transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-1',
        'disabled:cursor-not-allowed',
        variants[variant],
        sizes[size],
        block && 'w-full',
        className,
      )}
      {...props}
    >
      {loading ? (
        <Loader2 size={iconSize} className="animate-spin" />
      ) : (
        Icon && <Icon size={iconSize} strokeWidth={2} />
      )}
      {children}
    </button>
  );
}
