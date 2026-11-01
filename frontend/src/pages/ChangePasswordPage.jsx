import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import AuthLayout from '@/components/layout/AuthLayout';
import PasswordFields from '@/components/auth/PasswordFields';
import { Button, Field, Input } from '@/components/ui';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { isValidPassword } from '@/lib/password';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';

/**
 * 내 비밀번호 변경.
 * 환경변수로 만든 최초 관리자처럼 mustChangePassword 인 사람은 이 화면을 끝내야 다른 화면을 씁니다.
 */
export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const { isAuthenticated, mustChangePassword, applyNewToken, logout } = useAuthStore();

  const [current, setCurrent] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!isAuthenticated) return <Navigate to="/login" replace />;

  const canSubmit = current.length > 0 && isValidPassword(password) && password === confirmPassword;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    setError('');
    setSubmitting(true);
    try {
      const { accessToken } = await authApi.changePassword(current, password);
      applyNewToken(accessToken);
      toast.success('비밀번호를 바꿨습니다. 다른 기기의 로그인은 끊어졌습니다.');
      navigate('/home', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title={mustChangePassword ? '비밀번호를 바꿔 주세요' : '비밀번호 변경'}
      description={
        mustChangePassword
          ? '처음 로그인했습니다. 계속하려면 임시로 받은 비밀번호를 새 비밀번호로 바꿔야 합니다.'
          : '비밀번호를 바꾸면 다른 기기의 로그인은 모두 끊어집니다.'
      }
      footer={
        mustChangePassword ? (
          <button type="button" onClick={logout} className="font-medium text-gray-600 hover:text-gray-900">
            다른 계정으로 로그인
          </button>
        ) : (
          <button type="button" onClick={() => navigate(-1)} className="font-medium text-gray-600 hover:text-gray-900">
            돌아가기
          </button>
        )
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        <Field label="현재 비밀번호" htmlFor="pw-current" required>
          <Input
            id="pw-current"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            className="h-11"
            required
          />
        </Field>
        <PasswordFields
          value={password}
          confirmValue={confirmPassword}
          onChange={setPassword}
          onConfirmChange={setConfirmPassword}
          idPrefix="change"
        />
        {error && (
          <p role="alert" className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-[13px] text-red-700">
            <AlertCircle size={16} className="shrink-0" />
            {error}
          </p>
        )}
        <Button type="submit" size="lg" block loading={submitting} disabled={!canSubmit}>
          비밀번호 변경
        </Button>
      </form>
    </AuthLayout>
  );
}
