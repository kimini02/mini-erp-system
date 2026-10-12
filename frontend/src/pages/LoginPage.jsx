import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff, AlertCircle } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import AuthLayout, { AuthLinks } from '@/components/layout/AuthLayout';
import { Button, Field, Input } from '@/components/ui';

export default function LoginPage() {
  const navigate = useNavigate();

  // 상태 관리: 사용자 입력값 및 비밀번호 표시 여부
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // authStore에서 로그인 함수 가져오기
  const { login } = useAuthStore();

  /**
   * 로그인 버튼 클릭 시 실행되는 핸들러
   */
  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    // authStore의 login 함수를 호출 (팀원 가이드에 맞춰 id/password로 전달됨)
    const result = await login({ userId, password });
    setSubmitting(false);

    if (result?.success) {
      localStorage.setItem('userLoginId', userId); // 프로필용 loginId 임시 저장
      // 루트 경로에서 권한(Role)에 맞는 대시보드로 이동
      navigate('/', { replace: true });
    } else {
      // 로그인 실패 시 서버 에러 메시지 출력
      setError(result?.message || '아이디 또는 비밀번호를 확인해주세요.');
    }
  };

  return (
    <AuthLayout
      title="로그인"
      description="사내 계정으로 로그인하세요."
      footer={
        <>
          계정이 없으신가요?{' '}
          <button type="button" onClick={() => navigate('/signup')} className="font-medium text-blue-600 hover:text-blue-700">
            회원가입
          </button>
        </>
      }
    >
      <form onSubmit={handleLogin} className="space-y-4">
        <Field label="아이디" htmlFor="userId">
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
          <Input
            id="password"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호를 입력하세요"
            autoComplete="current-password"
            className="h-11"
            required
            trailing={
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="rounded p-1 text-gray-400 hover:text-gray-600"
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            }
          />
        </Field>

        {error && (
          <p className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-[13px] text-red-700">
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
