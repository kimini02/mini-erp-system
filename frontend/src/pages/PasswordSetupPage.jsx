import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import AuthLayout from '@/components/layout/AuthLayout';
import PasswordFields from '@/components/auth/PasswordFields';
import { Button } from '@/components/ui';
import { authApi } from '@/api/m1';
import { isValidPassword } from '@/lib/password';
import { getErrorCode, getErrorMessage } from '@/lib/errors';

/** 초대 메일 링크(/password/setup?token=…)로 첫 비밀번호를 정합니다. */
export default function PasswordSetupPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [expired, setExpired] = useState(!token);
  const [submitting, setSubmitting] = useState(false);

  const canSubmit = isValidPassword(password) && password === confirmPassword;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    setError('');
    setSubmitting(true);
    try {
      await authApi.setupPassword(token, password);
      navigate('/login?reason=password-set', { replace: true });
    } catch (err) {
      if (getErrorCode(err) === 'SETUP_TOKEN_INVALID') setExpired(true);
      else setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (expired) {
    return (
      <AuthLayout title="링크를 쓸 수 없습니다" description="비밀번호 설정 링크가 만료되었거나 이미 사용되었습니다.">
        <p className="text-sm leading-relaxed text-gray-600">
          링크는 받은 뒤 24시간 동안 한 번만 쓸 수 있습니다. 인사 담당자에게 초대 메일을 다시 보내 달라고 요청하세요.
        </p>
        <Button size="lg" block className="mt-8" onClick={() => navigate('/login')}>
          로그인 화면으로
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="비밀번호 설정" description="WorkFlow에 오신 것을 환영합니다. 처음 쓸 비밀번호를 정해 주세요.">
      <form onSubmit={handleSubmit} className="space-y-5">
        <PasswordFields
          value={password}
          confirmValue={confirmPassword}
          onChange={setPassword}
          onConfirmChange={setConfirmPassword}
          idPrefix="setup"
        />
        {error && (
          <p role="alert" className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-[13px] text-red-700">
            <AlertCircle size={16} className="shrink-0" />
            {error}
          </p>
        )}
        <Button type="submit" size="lg" block loading={submitting} disabled={!canSubmit}>
          비밀번호 설정
        </Button>
      </form>
    </AuthLayout>
  );
}
