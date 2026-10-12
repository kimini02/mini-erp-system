import { forwardRef } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';

const controlBase =
  'block w-full rounded-lg border text-sm text-gray-900 placeholder:text-gray-400 transition-colors ' +
  'focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-500';

const stateClass = (invalid) =>
  invalid
    ? 'border-red-400 focus:border-red-500 focus:ring-red-500/20'
    : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500/20';

/** 텍스트 입력. icon: 왼쪽 아이콘, trailing: 오른쪽 요소(비밀번호 보기 버튼 등) */
export const Input = forwardRef(function Input({ className, invalid, icon: Icon, trailing, ...props }, ref) {
  return (
    <div className="relative">
      {Icon && (
        <Icon size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
      )}
      <input
        ref={ref}
        className={cn(
          controlBase,
          stateClass(invalid),
          'h-10',
          Icon ? 'pl-9' : 'pl-3',
          trailing ? 'pr-10' : 'pr-3',
          props.readOnly ? 'bg-gray-50 text-gray-600' : 'bg-white',
          className
        )}
        {...props}
      />
      {trailing && <div className="absolute right-2 top-1/2 flex -translate-y-1/2 items-center">{trailing}</div>}
    </div>
  );
});

export const Select = forwardRef(function Select({ className, invalid, icon: Icon, children, ...props }, ref) {
  return (
    <div className="relative">
      {Icon && (
        <Icon size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
      )}
      <select
        ref={ref}
        className={cn(controlBase, stateClass(invalid), 'h-10 appearance-none bg-white pr-9', Icon ? 'pl-9' : 'pl-3', className)}
        {...props}
      >
        {children}
      </select>
      <ChevronDown size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-gray-400" />
    </div>
  );
});

export const Textarea = forwardRef(function Textarea({ className, invalid, rows = 4, ...props }, ref) {
  return (
    <textarea
      ref={ref}
      rows={rows}
      className={cn(controlBase, stateClass(invalid), 'resize-none bg-white px-3 py-2.5 leading-relaxed', className)}
      {...props}
    />
  );
});

/** 라벨 + 입력 + 도움말/에러를 묶는 래퍼 */
export function Field({ label, htmlFor, required, hint, error, className, children }) {
  return (
    <div className={cn('space-y-1.5', className)}>
      {label && (
        <label htmlFor={htmlFor} className="block text-[13px] font-medium text-gray-700">
          {label}
          {required && <span className="ml-0.5 text-red-500">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p className="text-xs text-red-600">{error}</p>
      ) : (
        hint && <p className="text-xs text-gray-500">{hint}</p>
      )}
    </div>
  );
}

/** 버튼형 단일 선택 (예: 우선순위 낮음/중간/높음) */
export function Segmented({ options, value, onChange, className }) {
  return (
    <div className={cn('inline-flex w-full rounded-lg border border-gray-300 bg-white p-0.5', className)}>
      {options.map((opt) => {
        const optValue = typeof opt === 'string' ? opt : opt.value;
        const optLabel = typeof opt === 'string' ? opt : opt.label;
        const active = optValue === value;
        return (
          <button
            key={optValue}
            type="button"
            onClick={() => onChange(optValue)}
            className={cn(
              'h-8 flex-1 rounded-md text-[13px] font-medium transition-colors',
              active ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
            )}
          >
            {optLabel}
          </button>
        );
      })}
    </div>
  );
}
