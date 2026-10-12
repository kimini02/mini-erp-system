import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, Eye, EyeOff } from 'lucide-react';
import api from '@/api/axios';
import AuthLayout from '@/components/layout/AuthLayout';
import { Button, Field, Input, Select } from '@/components/ui';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';

export default function SignupPage() {
  const navigate = useNavigate();

  // 1. 폼 데이터 상태 관리
  const [formData, setFormData] = useState({
    userName: '',
    rank: '',
    userId: '',
    email: '',
    password: '',
    confirmPassword: '',
    department: '',
  });

  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 2. 비밀번호 유효성 상세 상태 관리 (대/소문자/숫자/특수문자/길이)
  const [passwordValid, setPasswordValid] = useState({
    isLengthValid: false, // 8자 이상 20자 이하
    hasUpperCase: false,  // 영어 대문자 포함
    hasLowerCase: false,  // 영어 소문자 포함
    hasNumber: false,     // 숫자 포함
    hasSpecial: false,    // 특수문자 포함
    isMatch: false        // 비밀번호 확인 일치 여부
  });

  const [isEmailValid, setIsEmailValid] = useState(false);

  /**
   * 입력값 변경 및 실시간 유효성 검사 핸들러
   */
  const handleChange = (e) => {
    const { id, value } = e.target;

    setFormData(prev => {
      const newData = { ...prev, [id]: value };

      // 이메일 형식 체크 (RegEx)
      if (id === 'email') {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        setIsEmailValid(emailRegex.test(value));
      }

      // 비밀번호 복합 조건 체크 (대문자, 소문자, 숫자, 특수문자, 8~20자)
      if (id === 'password' || id === 'confirmPassword') {
        const pw = newData.password;
        setPasswordValid({
          isLengthValid: pw.length >= 8 && pw.length <= 20,
          hasUpperCase: /[A-Z]/.test(pw),
          hasLowerCase: /[a-z]/.test(pw),
          hasNumber: /[0-9]/.test(pw),
          hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(pw),
          isMatch: pw === newData.confirmPassword && newData.confirmPassword !== ''
        });
      }
      return newData;
    });
  };

  /**
   * 모든 조건이 충족되었는지 확인 (버튼 활성화 기준)
   */
  const isFormValid =
    formData.userId &&
    formData.userName &&
    formData.rank &&
    formData.department &&
    isEmailValid &&
    passwordValid.isLengthValid &&
    passwordValid.hasUpperCase &&
    passwordValid.hasLowerCase &&
    passwordValid.hasNumber &&
    passwordValid.hasSpecial &&
    passwordValid.isMatch;

  /**
   * 회원가입 제출 처리
   */
  const handleSignup = async (e) => {
    e.preventDefault();

    if (!isFormValid) {
      toast.error('입력 항목과 비밀번호 정책을 다시 확인해주세요.');
      return;
    }

    setSubmitting(true);
    try {
      const response = await api.post('/auth/signup', {
        id: formData.userId,
        name: formData.userName,
        email: formData.email,
        password: formData.password,
        position: formData.rank,
        // 백엔드 DTO 필드명인 departmentCode와 일치
        departmentCode: formData.department
      });

      if (response.data.success) {
        toast.success('회원가입이 완료되었습니다. 로그인해주세요.');
        navigate('/login');
      }
    } catch (error) {
      const errorMsg = error.response?.data?.error?.message ||
                       error.response?.data?.message ||
                       '서버 통신 중 에러가 발생했습니다.';
      toast.error(errorMsg);
      console.error('Signup Error:', error.response?.data);
    } finally {
      setSubmitting(false);
    }
  };

  const emailInvalid = !!formData.email && !isEmailValid;
  const confirmInvalid = !!formData.confirmPassword && !passwordValid.isMatch;

  return (
    <AuthLayout
      title="회원가입"
      description="계정을 만들고 WorkFlow를 시작하세요."
      footer={
        <>
          이미 계정이 있으신가요?{' '}
          <button type="button" onClick={() => navigate('/login')} className="font-medium text-blue-600 hover:text-blue-700">
            로그인
          </button>
        </>
      }
    >
      <form onSubmit={handleSignup} className="space-y-4">
        <Field label="아이디" htmlFor="userId" required>
          <Input id="userId" value={formData.userId} onChange={handleChange} placeholder="영문/숫자 조합" autoComplete="username" />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="이름" htmlFor="userName" required>
            <Input id="userName" value={formData.userName} onChange={handleChange} placeholder="홍길동" />
          </Field>
          <Field label="직급" htmlFor="rank" required>
            <Select id="rank" value={formData.rank} onChange={handleChange}>
              <option value="">선택</option>
              <option value="사원">사원</option>
              <option value="대리">대리</option>
              <option value="과장">과장</option>
              <option value="팀장">팀장</option>
            </Select>
          </Field>
        </div>

        <Field label="부서" htmlFor="department" required>
          <Select id="department" value={formData.department} onChange={handleChange}>
            <option value="">부서 선택</option>
            <option value="01">개발팀</option>
            <option value="02">유지보수팀</option>
            <option value="03">모바일개발팀</option>
          </Select>
        </Field>

        <Field label="이메일" htmlFor="email" required error={emailInvalid && '올바른 이메일 형식이 아닙니다.'}>
          <Input
            id="email"
            type="email"
            value={formData.email}
            onChange={handleChange}
            placeholder="name@company.com"
            invalid={emailInvalid}
            autoComplete="email"
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="비밀번호" htmlFor="password" required>
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={formData.password}
              onChange={handleChange}
              placeholder="8~20자"
              autoComplete="new-password"
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
          <Field label="비밀번호 확인" htmlFor="confirmPassword" required>
            <Input
              id="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              value={formData.confirmPassword}
              onChange={handleChange}
              placeholder="한 번 더 입력"
              invalid={confirmInvalid}
              autoComplete="new-password"
            />
          </Field>
        </div>

        {/* 비밀번호 정책 실시간 체크 */}
        <div className="grid grid-cols-3 gap-x-3 gap-y-1.5 rounded-lg bg-gray-50 px-3.5 py-3">
          <PolicyItem label="8~20자" isDone={passwordValid.isLengthValid} />
          <PolicyItem label="대문자" isDone={passwordValid.hasUpperCase} />
          <PolicyItem label="소문자" isDone={passwordValid.hasLowerCase} />
          <PolicyItem label="숫자" isDone={passwordValid.hasNumber} />
          <PolicyItem label="특수문자" isDone={passwordValid.hasSpecial} />
          <PolicyItem label="비밀번호 일치" isDone={passwordValid.isMatch} />
        </div>

        <Button type="submit" size="lg" block disabled={!isFormValid} loading={submitting} className="!mt-6">
          가입하기
        </Button>
      </form>
    </AuthLayout>
  );
}

/**
 * 비밀번호 정책 체크용 내부 컴포넌트
 */
const PolicyItem = ({ label, isDone }) => (
  <div className={cn('flex items-center gap-1.5 text-xs', isDone ? 'text-emerald-700' : 'text-gray-500')}>
    <Check size={13} strokeWidth={2.5} className={isDone ? 'text-emerald-600' : 'text-gray-300'} />
    {label}
  </div>
);
