import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, CheckCircle2, Eye, EyeOff, Info } from 'lucide-react';
import axios from '@/api/axios';
import AuthLayout, { AuthLinks } from '@/components/layout/AuthLayout';
import { Button, Field, Input } from '@/components/ui';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';

const STEPS = ['본인 확인', '인증번호 입력', '새 비밀번호'];

/**
 * 진행 단계 표시
 */
const Stepper = ({ currentStep }) => (
  <ol className="mb-8 flex items-center gap-2">
    {STEPS.map((label, i) => {
      const num = i + 1;
      const done = currentStep > num;
      const active = currentStep === num;
      return (
        <li key={label} className="flex flex-1 items-center gap-2">
          <span
            className={cn(
              'flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
              done || active ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-400'
            )}
          >
            {done ? <Check size={13} strokeWidth={3} /> : num}
          </span>
          <span className={cn('whitespace-nowrap text-[13px]', active ? 'font-medium text-gray-900' : 'text-gray-400')}>
            {label}
          </span>
          {num < STEPS.length && <span className={cn('h-px flex-1', done ? 'bg-blue-600' : 'bg-gray-200')} />}
        </li>
      );
    })}
  </ol>
);

export default function FindPwPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1); // 1:본인확인, 2:인증번호, 3:재설정, 4:완료
  const [showPw, setShowPw] = useState(false); // 비밀번호 보이기/숨기기
  const [targetUser, setTargetUser] = useState(null); // 찾은 유저의 정보를 임시 저장 (id 포함)

  // 입력 폼 상태 관리
  const [formData, setFormData] = useState({
    userId: '',
    email: '',
    authCode: '',
    newPassword: '',
    confirmPassword: ''
  });

  // 입력값 변경 핸들러
  const handleChange = (e) => {
    const { id, value } = e.target;
    setFormData(prev => ({ ...prev, [id]: value }));
  };

  /**
   * STEP 1: 사용자 확인 및 인증번호 발송
   */
  const handleVerifyUser = async (e) => {
    e.preventDefault();
    try {
      // 아이디와 이메일이 동시에 일치하는 유저 조회
      const response = await axios.get(`/users?userId=${formData.userId}&email=${formData.email}`);

      if (response.data && response.data.length > 0) {
        setTargetUser(response.data[0]); // 유저 객체 저장 (patch 통신 시 id 필요)
        setStep(2);
      } else {
        toast.error('일치하는 사용자 정보가 없습니다. 다시 확인해주세요.');
      }
    } catch (error) {
      console.error('사용자 확인 중 에러 발생:', error);
      toast.error('서버 연결에 실패했습니다. 잠시 후 다시 시도해주세요.');
    }
  };

  /**
   * STEP 2: 인증번호 확인
   */
  const handleVerifyCode = (e) => {
    e.preventDefault();
    // 가짜 서버이므로 데모 번호 123456으로 고정
    if (formData.authCode === '123456') {
      setStep(3);
    } else {
      toast.error('인증번호가 일치하지 않습니다.');
    }
  };

  /**
   * STEP 2-1: 인증번호 재발송
   */
  const handleResendCode = () => {
    setFormData(prev => ({ ...prev, authCode: '' })); // 입력창 초기화
    toast.info('인증번호를 다시 발송했습니다.');
  };

  /**
   * STEP 3: 실제 비밀번호 재설정 (PATCH)
   */
  const handleResetPassword = async (e) => {
    e.preventDefault();
    if (formData.newPassword !== formData.confirmPassword) {
      toast.error('비밀번호가 서로 일치하지 않습니다.');
      return;
    }
    if (formData.newPassword.length < 4) {
      toast.error('비밀번호는 최소 4자 이상이어야 합니다.');
      return;
    }

    try {
      // 해당 유저 데이터만 부분 수정(PATCH)
      await axios.patch(`/users/${targetUser.id}`, {
        password: formData.newPassword
      });

      setStep(4); // 완료 화면으로 이동
    } catch (error) {
      console.error('비밀번호 변경 중 에러 발생:', error);
      toast.error('비밀번호 변경에 실패했습니다. 다시 시도해주세요.');
    }
  };

  const links = (
    <AuthLinks
      links={[
        { label: '로그인', onClick: () => navigate('/login') },
        { label: '아이디 찾기', onClick: () => navigate('/find-id') },
      ]}
    />
  );

  if (step === 4) {
    return (
      <AuthLayout>
        <div className="text-center">
          <span className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50">
            <CheckCircle2 size={24} className="text-emerald-600" />
          </span>
          <h1 className="text-xl font-semibold text-gray-900">비밀번호가 재설정되었습니다</h1>
          <p className="mt-2 text-sm text-gray-500">새 비밀번호로 로그인하세요.</p>
        </div>
        <Button size="lg" block className="mt-8" onClick={() => navigate('/login')}>
          로그인하러 가기
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="비밀번호 찾기" description="본인 확인 후 비밀번호를 재설정할 수 있습니다." footer={links}>
      <Stepper currentStep={step} />

      {/* STEP 1: 본인확인 */}
      {step === 1 && (
        <form onSubmit={handleVerifyUser} className="space-y-4">
          <Field label="아이디" htmlFor="userId">
            <Input id="userId" value={formData.userId} onChange={handleChange} placeholder="아이디" required className="h-11" />
          </Field>
          <Field label="이메일" htmlFor="email">
            <Input
              id="email"
              type="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="가입 시 입력한 이메일"
              required
              className="h-11"
            />
          </Field>
          <Button type="submit" size="lg" block className="!mt-6">
            인증번호 발송
          </Button>
        </form>
      )}

      {/* STEP 2: 인증번호 입력 */}
      {step === 2 && (
        <form onSubmit={handleVerifyCode} className="space-y-4">
          <p className="flex items-start gap-2 rounded-lg bg-blue-50 px-3 py-2.5 text-[13px] text-blue-800">
            <Info size={16} className="mt-px shrink-0 text-blue-600" />
            <span>
              <b className="font-medium">{formData.email}</b>(으)로 인증번호를 보냈습니다. (데모: 123456)
            </span>
          </p>
          <Field label="인증번호" htmlFor="authCode">
            <div className="flex gap-2">
              <div className="flex-1">
                <Input
                  id="authCode"
                  maxLength={6}
                  value={formData.authCode}
                  onChange={handleChange}
                  placeholder="6자리 숫자"
                  inputMode="numeric"
                  className="h-11 tracking-[0.2em]"
                />
              </div>
              <Button variant="secondary" size="lg" onClick={handleResendCode}>
                재발송
              </Button>
            </div>
          </Field>
          <Button type="submit" size="lg" block className="!mt-6">
            인증 확인
          </Button>
        </form>
      )}

      {/* STEP 3: 새 비밀번호 설정 */}
      {step === 3 && (
        <form onSubmit={handleResetPassword} className="space-y-4">
          <Field label="새 비밀번호" htmlFor="newPassword">
            <Input
              id="newPassword"
              type={showPw ? 'text' : 'password'}
              value={formData.newPassword}
              onChange={handleChange}
              placeholder="8자 이상 권장"
              autoComplete="new-password"
              className="h-11"
              trailing={
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  className="rounded p-1 text-gray-400 hover:text-gray-600"
                  aria-label={showPw ? '비밀번호 숨기기' : '비밀번호 보기'}
                >
                  {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              }
            />
          </Field>
          <Field label="새 비밀번호 확인" htmlFor="confirmPassword">
            <Input
              id="confirmPassword"
              type={showPw ? 'text' : 'password'}
              value={formData.confirmPassword}
              onChange={handleChange}
              placeholder="한 번 더 입력"
              autoComplete="new-password"
              className="h-11"
            />
          </Field>
          <Button type="submit" size="lg" block className="!mt-6">
            비밀번호 재설정
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
