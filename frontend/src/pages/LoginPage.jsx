import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle, Info } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import AuthLayout, { AuthLinks } from '@/components/layout/AuthLayout';
import { Button, Field, Input } from '@/components/ui';
import PasswordInput from '@/components/auth/PasswordInput';

const REASON_MESSAGES = {
  revoked: '계정 상태나 비밀번호가 바뀌어 로그아웃되었습니다. 다시 로그인해 주세요.',
  expired: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  'password-set': '비밀번호를 설정했습니다. 새 비밀번호로 로그인하세요.',
};

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const notice = REASON_MESSAGES[searchParams.get('reason')];

  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { login } = useAuthStore();

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const result = await login({ userId, password });
    setSubmitting(false);

    if (!result?.success) {
      setError(result?.message || '아이디 또는 비밀번호를 확인해주세요.');
      return;
    }
    if (result.mustChangePassword) {
      navigate('/me/password', { replace: true });
      return;
    }
    navigate(location.state?.from || '/home', { replace: true });
  };

  return (
    <AuthLayout
      title="로그인"
      description="사내 계정으로 로그인하세요."
      footer="계정은 관리자가 만들어 드립니다. 초대 메일을 받지 못했다면 인사 담당자에게 문의하세요."
    >
      {notice && (
        <p className="mb-5 flex items-start gap-2 rounded-lg bg-blue-50 px-3 py-2.5 text-[13px] text-blue-800">
          <Info size={16} className="mt-px shrink-0" />
          {notice}
        </p>
      )}

      <form onSubmit={handleLogin} className="space-y-4">
        <Field label="아이디" htmlFor="userId" hint="입사연도와 번호로 된 사번입니다 (예: 2026004)">
          <Input
            id="userId"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="아이디를 입력하세요"
            autoComplete="username"
            className="h-11"
            required
          />
        </Field>

        <Field label="비밀번호" htmlFor="password">
          <PasswordInput
            id="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호를 입력하세요"
            autoComplete="current-password"
            className="h-11"
            required
          />
        </Field>

        {error && (
          <p role="alert" className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-[13px] text-red-700">
            <AlertCircle size={16} className="shrink-0" />
            {error}
          </p>
        )}

        <Button type="submit" size="lg" block loading={submitting} className="!mt-6">
          로그인
        </Button>
      </form>

      <div className="mt-5 text-[13px]">
        <AuthLinks
          links={[
            { label: '아이디 찾기', onClick: () => navigate('/find-id') },
            { label: '비밀번호 찾기', onClick: () => navigate('/find-pw') },
          ]}
        />
      </div>
    </AuthLayout>
  );
}
