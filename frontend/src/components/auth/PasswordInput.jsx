import { forwardRef, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { Input } from '@/components/ui';

/** Each field controls its own visibility. The native button supports Tab, Enter and Space. */
const PasswordInput = forwardRef(function PasswordInput({ label = '비밀번호', ...props }, ref) {
  const [visible, setVisible] = useState(false);

  return (
    <Input
      {...props}
      ref={ref}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          disabled={props.disabled}
          onClick={() => setVisible((previous) => !previous)}
          className="rounded p-1 text-gray-500 hover:text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed"
          aria-label={`${label} ${visible ? '숨기기' : '보기'}`}
          aria-controls={props.id}
          aria-pressed={visible}
        >
          {visible ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
        </button>
      }
    />
  );
});

export default PasswordInput;
