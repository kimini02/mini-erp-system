import { useState, useEffect } from 'react';
import api from '../api/axios';
import { useAuthStore } from '../store/authStore';
import { PageHeader } from '@/components/ui';
import LeaveBalanceCard from '../components/leave/LeaveBalanceCard';
import LeaveApplyForm from '../components/leave/LeaveApplyForm';
import OvertimeApplyForm from '../components/overtime/OvertimeApplyForm';

const LeaveApplyPage = () => {
  const { user } = useAuthStore(); // 현재 로그인한 사용자 정보
  const [leaveData, setLeaveData] = useState({
    totalAnnualLeave: 0,
    usedAnnualLeave: 0,
    remainingAnnualLeave: 0
  });

  // 잔여 연차 조회
  useEffect(() => {
    const fetchLeaveData = async () => {
      if (!user?.id) return;
      try {
        const response = await api.get('/leave/balance');
        if (response.data.success) {
          // 백엔드 DTO(LeaveBalanceResponseDto) 데이터를 그대로 저장
          setLeaveData(response.data.data);
        }
      } catch (error) {
        console.error('정보 로딩 실패:', error);
      }
    };

    fetchLeaveData();
  }, [user]);

  return (
    <>
      <PageHeader title="연차/특근 신청" description="잔여 연차를 확인하고 연차 또는 특근을 신청하세요." />

      <LeaveBalanceCard
        totalAnnualLeave={leaveData.totalAnnualLeave}
        usedAnnualLeave={leaveData.usedAnnualLeave}
        remainingAnnualLeave={leaveData.remainingAnnualLeave}
      />

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <LeaveApplyForm remainingBalance={leaveData.remainingAnnualLeave} user={user} />
        <OvertimeApplyForm user={user} />
      </div>
    </>
  );
};

export default LeaveApplyPage;
