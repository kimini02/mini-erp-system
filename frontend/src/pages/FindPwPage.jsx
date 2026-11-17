import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, CheckCircle2, Info } from 'lucide-react';
import { authApi } from '@/api/auth';
import PasswordFields from '@/components/auth/PasswordFields';
import { isValidPassword } from '@/lib/password';
import { getErrorCode, getErrorMessage } from '@/lib/errors';
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
              done || active ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-400',
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
  const [resetProof, setResetProof] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState('');
  const [formData, setFormData] = useState({ email: '', authCode: '', newPassword: '', confirmPassword: '' });

  const handleChange = (e) => {
    const { id, value } = e.target;
    setFormData((prev) => ({ ...prev, [id]: value }));
  };

  // The same message is shown whether an account exists or not.
  const requestCode = async () => {
    const result = await authApi.requestPasswordReset(formData.email);
    setNotice(result.message);
    setResetProof('');
    setFormData((prev) => ({ ...prev, authCode: '' }));
  };

  const handleVerifyUser = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      await requestCode();
      setStep(2);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const handleVerifyCode = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      const result = await authApi.verifyPasswordReset(formData.email, formData.authCode);
      setResetProof(result.resetProof);
      setStep(3);
    } catch (error) {
      const code = getErrorCode(error);
      toast.error(['OTP_INVALID', 'OTP_EXPIRED', 'INVALID_RESET_REQUEST'].includes(code)
        ? '이메일과 인증번호를 확인하거나 인증번호를 다시 요청해 주세요.'
        : getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const handleResendCode = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      await requestCode();
      toast.info('인증번호 발송을 다시 요청했습니다.');
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const canReset = isValidPassword(formData.newPassword) && formData.newPassword === formData.confirmPassword;
  const handleResetPassword = async (e) => {
    e.preventDefault();
    if (submitting || !canReset) return;
    setSubmitting(true);
    try {
      await authApi.confirmPasswordReset(resetProof, formData.newPassword, formData.confirmPassword);
      setResetProof('');
      setFormData((prev) => ({ ...prev, authCode: '', newPassword: '', confirmPassword: '' }));
      setStep(4);
    } catch (error) {
      toast.error(getErrorMessage(error));
      if (getErrorCode(error) === 'RESET_PROOF_INVALID') {
        setResetProof('');
        setFormData((prev) => ({ ...prev, authCode: '', newPassword: '', confirmPassword: '' }));
        setStep(1);
      }
    } finally {
      setSubmitting(false);
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
          <Field label="이메일" htmlFor="email">
            <Input
              id="email"
              type="email"
              disabled={submitting}
              value={formData.email}
              onChange={handleChange}
              placeholder="등록된 이메일"
              required
              className="h-11"
            />
          </Field>
          <Button type="submit" size="lg" block loading={submitting} className="!mt-6">
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
              {notice} 메일을 받지 못했다면 입력한 이메일과 스팸함을 확인해 주세요.
            </span>
          </p>
          {(import.meta.env.MODE === 'mock' || import.meta.env.VITE_USE_MOCK === 'true') && (
            <p className="text-xs text-gray-500">목 모드 인증번호: 123456 (실제 메일은 보내지 않습니다.)</p>
          )}
          <Field label="인증번호" htmlFor="authCode">
            <div className="flex gap-2">
              <div className="flex-1">
                <Input
                  id="authCode"
                  maxLength={6}
                  minLength={6}
                  pattern="[0-9]{6}"
                  autoComplete="one-time-code"
                  required
                  value={formData.authCode}
                  onChange={handleChange}
                  placeholder="6자리 숫자"
                  inputMode="numeric"
                  className="h-11 tracking-[0.2em]"
                />
              </div>
              <Button variant="secondary" size="lg" onClick={handleResendCode} disabled={submitting}>
                재발송
              </Button>
            </div>
          </Field>
          <Button type="submit" size="lg" block loading={submitting} className="!mt-6">
            인증 확인
          </Button>
          <Button variant="ghost" block disabled={submitting} onClick={() => setStep(1)}>
            이메일 다시 입력
          </Button>
        </form>
      )}

      {/* STEP 3: 새 비밀번호 설정 */}
      {step === 3 && (
        <form onSubmit={handleResetPassword} className="space-y-4">
          <PasswordFields
            value={formData.newPassword}
            confirmValue={formData.confirmPassword}
            onChange={(value) => setFormData((prev) => ({ ...prev, newPassword: value }))}
            onConfirmChange={(value) => setFormData((prev) => ({ ...prev, confirmPassword: value }))}
            idPrefix="reset"
          />
          <Button type="submit" size="lg" block loading={submitting} disabled={!canReset} className="!mt-6">
            비밀번호 재설정
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
