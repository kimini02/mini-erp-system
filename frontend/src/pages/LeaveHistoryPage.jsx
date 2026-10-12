import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { FilePlus2 } from 'lucide-react';
import api from '../api/axios';
import { useAuthStore } from '../store/authStore';
import { getRoleInfo } from '@/lib/roles';
import { Button, Card, CardHeader, PageHeader, PageLoader } from '@/components/ui';
import LeaveHistoryTable from '../components/leave/LeaveHistoryTable';
import LeaveStatusCards from '../components/leave/LeaveStatusCards';
import OvertimeHistoryTable from '../components/overtime/OvertimeHistoryTable';

const LeaveHistoryPage = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [leaveData, setLeaveData] = useState([]);
  const [overtimeData, setOvertimeData] = useState([]);
  const [loading, setLoading] = useState(true);

  // 권한 확인 변수
  const userRole = user?.role || '';
  const { basePath } = getRoleInfo(user);

  useEffect(() => {
    const fetchHistory = async () => {
      if (!user?.id) {
        setLoading(false);
        return;
      }
      try {
        const leaveRes = await api.get(`/leave/my`);
        if (leaveRes.data.success) {
          setLeaveData(leaveRes.data.data || []);
        }

        // 특근: 권한에 따른 분기 처리
        const isStaff = userRole === 'USER';
        const overtimeEndpoint = isStaff ? `/overtime/my` : `/overtime/all`;

        const overtimeRes = await api.get(overtimeEndpoint);

        if (overtimeRes.data.success) {
          const rawOvData = overtimeRes.data.data || [];

          if (isStaff) {
            // 사원은 가져온 데이터 그대로 사용
            setOvertimeData(rawOvData);
          } else {
            // 팀장/관리자는 전체 목록 중 본인 ID와 일치하는 것만 필터링
            const myOvertimes = rawOvData.filter(item =>
              String(item.requesterId) === String(user?.id)
            );
            setOvertimeData(myOvertimes);
          }
        }
      } catch (error) {
        console.error('내역 로딩 실패:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, [user, userRole]);

  return (
    <>
      <PageHeader
        title="신청 내역"
        description="내 연차·특근 신청 현황과 결재 결과를 확인하세요."
        actions={
          <Button icon={FilePlus2} onClick={() => navigate(`${basePath}/leaves/new`)}>
            새 신청
          </Button>
        }
      />

      {loading ? (
        <PageLoader />
      ) : (
        <div className="space-y-4">
          <LeaveStatusCards leaveData={leaveData} overtimeData={overtimeData} />

          <Card className="overflow-hidden">
            <CardHeader title="연차 신청" description={`총 ${leaveData.length}건`} />
            <LeaveHistoryTable historyData={leaveData} />
          </Card>

          <Card className="overflow-hidden">
            <CardHeader title="특근 신청" description={`총 ${overtimeData.length}건`} />
            <OvertimeHistoryTable historyData={overtimeData} />
          </Card>
        </div>
      )}
    </>
  );
};

export default LeaveHistoryPage;
