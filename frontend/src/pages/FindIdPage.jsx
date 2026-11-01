import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import axios from '@/api/axios';
import AuthLayout, { AuthLinks } from '@/components/layout/AuthLayout';
import { Button, Field, Input } from '@/components/ui';
import { toast } from '@/lib/toast';

export default function FindIdPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [foundId, setFoundId] = useState('');
  const [formData, setFormData] = useState({ userName: '', email: '' });

  const handleChange = (e) => {
    const { id, value } = e.target;
    setFormData((prev) => ({ ...prev, [id]: value }));
  };

  /**
   * 아이디 찾기 제출 핸들러
   * 이름과 이메일이 일치하는 사용자를 조회합니다.
   */
  const handleFindId = async (e) => {
    e.preventDefault();

    try {
      // 1. 이름(name)과 이메일(email)이 일치하는 유저를 쿼리 스트링으로 조회합니다.
      const response = await axios.get(`/users?userName=${formData.userName}&email=${formData.email}`);

      // 2. 검색 결과가 있는지 확인합니다.
      if (response.data && response.data.length > 0) {
        // 결과 배열의 첫 번째 유저의 실제 아이디를 저장합니다.
        const user = response.data[0];
        setFoundId(user.userId);
        setStep(2); // 결과 화면으로 이동
      } else {
        toast.error('입력하신 정보와 일치하는 아이디를 찾을 수 없습니다.');
      }
    } catch (error) {
      console.error('아이디 찾기 통신 에러:', error);
      toast.error('서버 연결에 실패했습니다. 잠시 후 다시 시도해주세요.');
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
          <h1 className="text-xl font-semibold text-gray-900">아이디를 찾았습니다</h1>
          <p className="mt-2 text-sm text-gray-500">입력하신 정보와 일치하는 계정입니다.</p>
        </div>

        <div className="my-8 rounded-lg border border-gray-200 bg-gray-50 px-5 py-4 text-center">
          <p className="text-xs text-gray-500">아이디</p>
          <p className="mt-1 text-lg font-semibold text-gray-900">{foundId}</p>
        </div>

        <Button size="lg" block onClick={() => navigate('/login')}>
          로그인하러 가기
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="아이디 찾기" description="가입할 때 입력한 이름과 이메일을 입력하세요." footer={links}>
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
        <Button type="submit" size="lg" block className="!mt-6">
          아이디 찾기
        </Button>
      </form>
    </AuthLayout>
  );
}
