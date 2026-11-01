import { Check, Circle } from 'lucide-react';
import { PASSWORD_RULES } from '@/lib/password';
import { cn } from '@/lib/cn';
import { Field, Input } from '@/components/ui';

/** 새 비밀번호 + 확인 입력과 정책 체크 목록 */
export default function PasswordFields({ value, confirmValue, onChange, onConfirmChange, idPrefix = 'pw' }) {
  const mismatch = confirmValue.length > 0 && value !== confirmValue;

  return (
    <div className="space-y-4">
      <Field label="새 비밀번호" htmlFor={`${idPrefix}-new`} required>
        <Input
          id={`${idPrefix}-new`}
          type="password"
          autoComplete="new-password"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="h-11"
          aria-describedby={`${idPrefix}-rules`}
          required
        />
      </Field>
      <ul
        id={`${idPrefix}-rules`}
        className="grid grid-cols-2 gap-x-4 gap-y-1.5 sm:grid-cols-3"
        aria-label="비밀번호 조건"
      >
        {PASSWORD_RULES.map((rule) => {
          const ok = rule.test(value);
          return (
            <li
              key={rule.id}
              className={cn('flex items-center gap-1.5 text-xs', ok ? 'text-emerald-700' : 'text-gray-500')}
            >
              {ok ? <Check size={13} strokeWidth={2.5} /> : <Circle size={11} />}
              {rule.label}
              <span className="sr-only">{ok ? '충족' : '미충족'}</span>
            </li>
          );
        })}
      </ul>
      <Field
        label="새 비밀번호 확인"
        htmlFor={`${idPrefix}-confirm`}
        required
        error={mismatch ? '비밀번호가 서로 다릅니다.' : undefined}
      >
        <Input
          id={`${idPrefix}-confirm`}
          type="password"
          autoComplete="new-password"
          value={confirmValue}
          onChange={(e) => onConfirmChange(e.target.value)}
          invalid={mismatch}
          className="h-11"
          required
        />
      </Field>
    </div>
  );
}
