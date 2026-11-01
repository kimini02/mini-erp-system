import { Check } from 'lucide-react';
import { cn } from '@/lib/cn';

/** 역할 선택 칩 (역할을 바꿀 권한이 있을 때만) */
export default function RoleChips({ roles, value, onChange }) {
  const toggle = (code) => onChange(value.includes(code) ? value.filter((c) => c !== code) : [...value, code]);
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="역할">
      {roles.map((r) => {
        const on = value.includes(r.code);
        return (
          <button
            key={r.code}
            type="button"
            role="checkbox"
            aria-checked={on}
            onClick={() => toggle(r.code)}
            className={cn(
              'inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[13px] font-medium transition-colors',
              on ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-300 text-gray-600 hover:border-gray-400',
            )}
          >
            {on && <Check size={13} strokeWidth={2.5} />}
            {r.name}
          </button>
        );
      })}
    </div>
  );
}

/* ───────── 등록 ───────── */
