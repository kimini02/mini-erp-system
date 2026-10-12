import React, { useState, useEffect } from 'react';
import { CheckCircle2, Eye, EyeOff, Mail } from 'lucide-react';
import axios from '@/api/axios';
import { toast } from '@/lib/toast';
import { Avatar, Badge, Button, Field, Input, InfoRow, Modal } from '@/components/ui';

/**
 * 내 프로필 모달 (상단 사용자 메뉴 → 내 프로필)
 */
const ProfilePage = ({ isOpen, onClose, user: initialUser }) => {
  const [mode, setMode] = useState('profile');
  const [step, setStep] = useState(1);
  const [showPw, setShowPw] = useState(false);
  const [formData, setFormData] = useState({
    authCode: '',
    newPassword: '',
    confirmPassword: '',
    resetProof: ''
  });

  const [serverUser, setServerUser] = useState(null);

  useEffect(() => {
    const fetchUserData = async () => {
      const id = initialUser?.id || initialUser?.userId;
      if (isOpen && id) {
        try {
          const response = await axios.get(`/users/${id}`);
          setServerUser(response.data);
        } catch (error) {
          console.error('상세조회 실패:', error);
        }
      }
    };
    fetchUserData();
  }, [isOpen, initialUser]);

  if (!isOpen) return null;

  const d = serverUser?.data || serverUser || initialUser || {};

  const displayInfo = {
    name: d.name || '이름 미확인',
    // 서버 데이터(d.loginId)가 없으면 로컬 스토리지에서 가져오기
    id: d.loginId || localStorage.getItem('userLoginId') || d.userId || 'ID 미확인',
    email: d.email || '이메일 미확인',
    position: d.position || '직급 미확인',
    dept: d.departmentName || d.department || d.departmentCode || '소속 부서 미지정',
    leave: d.remainingAnnualLeave ?? 15
  };

  const handleClose = () => {
    setMode('profile');
    setStep(1);
    setFormData({ authCode: '', newPassword: '', confirmPassword: '', resetProof: '' });
    onClose();
  };

  const handleRequestAuth = async () => {
    try {
      const response = await axios.post('/auth/password/reset/request', { email: displayInfo.email });
      if (response.data.success) {
        toast.success('인증번호를 발송했습니다.');
        setStep(2);
      }
    } catch (error) {
      toast.error(error.response?.data?.message || '인증번호 발송에 실패했습니다.');
    }
  };

  const handleVerifyCode = async () => {
    try {
      const response = await axios.post('/auth/password/reset/verify', {
        email: displayInfo.email,
        verificationCode: formData.authCode
      });
      if (response.data.success) {
        setFormData(prev => ({ ...prev, resetProof: response.data.data.resetProof }));
        setStep(3);
      }
    } catch {
      toast.error('인증번호가 올바르지 않습니다.');
    }
  };

  const handleConfirmReset = async () => {
    if (formData.newPassword !== formData.confirmPassword) {
      toast.error('비밀번호가 일치하지 않습니다.');
      return;
    }
    try {
      const response = await axios.post('/auth/password/reset/confirm', {
        resetProof: formData.resetProof,
        newPassword: formData.newPassword,
        newPasswordConfirm: formData.confirmPassword
      });
      if (response.data.success) {
        setStep(4);
      }
    } catch (error) {
      toast.error(error.response?.data?.message || '비밀번호 변경에 실패했습니다.');
    }
  };

  // ---- 프로필 보기 ----
  if (mode === 'profile') {
    return (
      <Modal
        open
        onClose={handleClose}
        title="내 프로필"
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={handleClose}>닫기</Button>
            <Button onClick={() => setMode('changePw')}>비밀번호 변경</Button>
          </>
        }
      >
        <div className="flex items-center gap-4 pb-4">
          <Avatar name={displayInfo.name} size="xl" />
          <div>
            <p className="text-lg font-semibold text-gray-900">{displayInfo.name}</p>
            <div className="mt-1 flex items-center gap-1.5">
              <Badge tone="blue">{displayInfo.position}</Badge>
              <span className="text-[13px] text-gray-500">{displayInfo.dept}</span>
            </div>
          </div>
        </div>
        <dl className="divide-y divide-gray-100 border-t border-gray-100">
          <InfoRow label="아이디" value={displayInfo.id} />
          <InfoRow label="이메일" value={displayInfo.email} />
          <InfoRow label="소속 부서" value={displayInfo.dept} />
          <InfoRow label="잔여 연차" value={<span className="text-blue-600">{displayInfo.leave}일</span>} />
        </dl>
      </Modal>
    );
  }

  // ---- 비밀번호 변경 ----
  const stepFooter = {
    1: <Button block onClick={handleRequestAuth}>인증번호 발송</Button>,
    2: <Button block onClick={handleVerifyCode}>인증 확인</Button>,
    3: <Button block onClick={handleConfirmReset}>비밀번호 변경</Button>,
    4: <Button block onClick={handleClose}>확인</Button>,
  };

  return (
    <Modal
      open
      onClose={handleClose}
      title="비밀번호 변경"
      description={step < 4 ? `${step} / 3 단계` : undefined}
      size="sm"
      footer={stepFooter[step]}
    >
      {step === 1 && (
        <div className="flex items-start gap-3 rounded-lg bg-gray-50 p-4">
          <Mail size={18} className="mt-0.5 shrink-0 text-gray-400" />
          <p className="text-sm text-gray-600">
            보안을 위해 <b className="font-medium text-gray-900">{displayInfo.email}</b>(으)로 인증번호를 발송합니다.
          </p>
        </div>
      )}

      {step === 2 && (
        <Field label="인증번호" htmlFor="pw-code" hint="이메일로 받은 6자리 숫자를 입력하세요.">
          <Input
            id="pw-code"
            value={formData.authCode}
            onChange={(e) => setFormData({ ...formData, authCode: e.target.value })}
            placeholder="000000"
            maxLength={6}
            inputMode="numeric"
            className="tracking-[0.2em]"
            autoFocus
          />
        </Field>
      )}

      {step === 3 && (
        <div className="space-y-4">
          <Field label="새 비밀번호" htmlFor="pw-new">
            <Input
              id="pw-new"
              type={showPw ? 'text' : 'password'}
              value={formData.newPassword}
              onChange={(e) => setFormData({ ...formData, newPassword: e.target.value })}
              autoComplete="new-password"
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
          <Field label="비밀번호 확인" htmlFor="pw-confirm">
            <Input
              id="pw-confirm"
              type={showPw ? 'text' : 'password'}
              value={formData.confirmPassword}
              onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
              autoComplete="new-password"
            />
          </Field>
        </div>
      )}

      {step === 4 && (
        <div className="flex flex-col items-center py-4 text-center">
          <span className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50">
            <CheckCircle2 size={24} className="text-emerald-600" />
          </span>
          <p className="font-medium text-gray-900">비밀번호가 변경되었습니다.</p>
        </div>
      )}
    </Modal>
  );
};

export default ProfilePage;
