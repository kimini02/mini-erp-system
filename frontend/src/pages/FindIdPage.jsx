import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { authApi } from '@/api/auth';
import { getErrorMessage } from '@/lib/errors';
import AuthLayout, { AuthLinks } from '@/components/layout/AuthLayout';
import { Button, Field, Input } from '@/components/ui';
import { toast } from '@/lib/toast';

export default function FindIdPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formData, setFormData] = useState({ userName: '', email: '' });

  const handleChange = (e) => {
    const { id, value } = e.target;
    setFormData((prev) => ({ ...prev, [id]: value }));
  };

  const handleFindId = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      const result = await authApi.findId(formData.userName, formData.email);
      setMessage(result.message);
      setStep(2);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const links = (
    <AuthLinks
      links={[
        { label: '로그인', onClick: () => navigate('/login') },
        { label: '비밀번호 찾기', onClick: () => navigate('/find-pw') },
      ]}
    />
  );

  if (step === 2) {
    return (
      <AuthLayout footer={links}>
        <div className="text-center">
          <span className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50">
            <CheckCircle2 size={24} className="text-emerald-600" />
          </span>
          <h1 className="text-xl font-semibold text-gray-900">아이디 안내 요청 완료</h1>
          <p className="mt-2 text-sm text-gray-500">{message}</p>
        </div>

        <p className="my-8 text-center text-sm text-gray-500">
          메일을 받지 못했다면 입력한 정보와 스팸함을 확인하거나 인사 담당자에게 문의해 주세요.
        </p>

        <Button size="lg" block onClick={() => navigate('/login')}>
          로그인하러 가기
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="아이디 찾기" description="사원 정보에 등록된 이름과 이메일을 입력하세요." footer={links}>
      <form onSubmit={handleFindId} className="space-y-4">
        <Field label="이름" htmlFor="userName">
          <Input
            id="userName"
            required
            placeholder="홍길동"
            value={formData.userName}
            onChange={handleChange}
            className="h-11"
          />
        </Field>
        <Field label="이메일" htmlFor="email">
          <Input
            id="email"
            type="email"
            required
            placeholder="name@company.com"
            value={formData.email}
            onChange={handleChange}
            className="h-11"
          />
        </Field>
        <Button type="submit" size="lg" block loading={submitting} className="!mt-6">
          아이디 찾기
        </Button>
      </form>
    </AuthLayout>
  );
}
