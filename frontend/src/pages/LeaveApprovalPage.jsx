import { useState, useEffect } from 'react';
import { Hourglass, CheckCircle2, XCircle } from 'lucide-react';
import api from '../api/axios';
import { useAuthStore } from '../store/authStore';
import { useApprovalStore } from '../store/approvalStore';
import { DEPARTMENT_LABELS, LEAVE_TYPE_LABELS, ROLE_LABELS } from '@/lib/labels';
import { confirm } from '@/lib/confirm';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Avatar,
  Badge,
  Button,
  Card,
  Field,
  Modal,
  PageHeader,
  StatCard,
  StatusBadge,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
  Textarea,
} from '@/components/ui';

/** 신청자 셀: 아바타 + 이름 + 직급/부서 */
const RequesterCell = ({ name, role, departmentCode }) => (
  <div className="flex items-center gap-2.5">
    <Avatar name={name} size="sm" />
    <div className="min-w-0">
      <p className="font-medium text-gray-900">{name}</p>
      <p className="text-xs text-gray-500">
        {ROLE_LABELS[role] || '사원'} · {DEPARTMENT_LABELS[departmentCode] || '소속 없음'}
      </p>
    </div>
  </div>
);

const ActionButtons = ({ onApprove, onReject }) => (
  <div className="flex justify-end gap-1.5">
    <Button size="sm" onClick={onApprove}>승인</Button>
    <Button size="sm" variant="danger-outline" onClick={onReject}>반려</Button>
  </div>
);

const LeaveApprovalPage = () => {
  const [requests, setRequests] = useState([]); // 연차 데이터
  const [overtimes, setOvertimes] = useState([]); // 특근 데이터
  const [tab, setTab] = useState('leave');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [rejectReason, setRejectReason] = useState('');

  const { user } = useAuthStore();
  const refreshInbox = useApprovalStore((s) => s.fetchPending);

  // 관리소장/팀장 구분
  const userRole = user?.role || '';
  const userPosition = user?.position || '';
  const isChief = userRole === 'ADMIN' || userPosition === '관리소장';

  // 데이터 로드 및 권한별 데이터 필터링
  useEffect(() => {
    const fetchRequests = async () => {
      try {
        const response = await api.get('/leave/all');

        if (response.data && response.data.success) {
          const rawData = response.data.data || [];
          const filteredData = isChief
            // [관리소장] 팀장(TEAM_LEADER)의 신청 내역만
            ? rawData.filter(item => item.userRole === 'TEAM_LEADER')
            // [팀장] 같은 팀 사원(USER)의 신청 내역만
            : rawData.filter(item => item.userRole === 'USER'
                && String(item.departmentCode) === String(user?.departmentCode));
          setRequests(filteredData);
        }

        // 특근
        const overtimeResponse = await api.get('/overtime/all');

        if (overtimeResponse.data && overtimeResponse.data.success) {
          const rawOvData = overtimeResponse.data.data || [];
          const filteredOvData = isChief
            ? rawOvData.filter(item => item.userRole === 'TEAM_LEADER')
            : rawOvData.filter(item => item.userRole === 'USER'
                && String(item.departmentCode) === String(user?.departmentCode));
          setOvertimes(filteredOvData);
        }
      } catch (error) {
        console.error('데이터 로드 중 오류:', error);
        setRequests([]);
        setOvertimes([]);
      }
    };

    // 유저 정보가 있을 때만 호출
    if (userRole) {
      fetchRequests();
    }
  }, [userRole, isChief, user?.departmentCode]);

  // 상단 통계 카운트
  const countBy = (status) =>
    requests.filter(r => r.appStatus === status).length + overtimes.filter(r => r.status === status).length;

  // 연차 승인 처리
  const handleApprove = async (req) => {
    const ok = await confirm({
      title: '연차를 승인하시겠습니까?',
      description: `${req.requesterName} · ${LEAVE_TYPE_LABELS[req.appType] || req.appType} · ${req.startDate} ~ ${req.endDate}`,
      confirmText: '승인',
    });
    if (!ok) return;
    try {
      const response = await api.patch(`/leave/${req.appId}/approve`);
      if (response.data.success) {
        setRequests(prev => prev.map(r => (r.appId === req.appId ? { ...r, appStatus: 'APPROVED' } : r)));
        toast.success('승인되었습니다.');
        refreshInbox(user);
      }
    } catch {
      toast.error('처리 중 오류가 발생했습니다.');
    }
  };

  // 연차 반려 처리
  const handleRejectSubmit = async () => {
    if (!rejectReason.trim()) {
      toast.error('반려 사유를 입력하세요.');
      return;
    }
    try {
      const appId = selectedRequest.appId;
      const response = await api.patch(`/leave/${appId}/reject`, { rejectReason });
      if (response.data.success) {
        setRequests(prev => prev.map(req =>
          req.appId === appId ? { ...req, appStatus: 'REJECTED', rejectReason } : req
        ));
        closeRejectModal();
        toast.success('반려되었습니다.');
        refreshInbox(user);
      }
    } catch {
      toast.error('서버 통신에 실패했습니다.');
    }
  };

  const closeRejectModal = () => {
    setIsModalOpen(false);
    setRejectReason('');
  };

  // 특근 승인 처리
  const handleOvertimeApprove = async (ov) => {
    const ok = await confirm({
      title: '특근을 승인하시겠습니까?',
      description: `${ov.requesterName} · ${ov.overtimeDate} ${ov.startTime?.slice(0, 5)}~${ov.endTime?.slice(0, 5)}`,
      confirmText: '승인',
    });
    if (!ok) return;
    try {
      const res = await api.patch(`/overtime/${ov.id}/approve`);
      if (res.data.success) {
        setOvertimes(prev => prev.map(o => (o.id === ov.id ? { ...o, status: 'APPROVED' } : o)));
        toast.success('특근이 승인되었습니다.');
        refreshInbox(user);
      }
    } catch {
      toast.error('처리 중 오류가 발생했습니다.');
    }
  };

  // 특근 반려 처리
  const handleOvertimeReject = async (ov) => {
    const ok = await confirm({
      title: '특근을 반려하시겠습니까?',
      description: `${ov.requesterName} · ${ov.overtimeDate}`,
      confirmText: '반려',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      const res = await api.patch(`/overtime/${ov.id}/reject`);
      if (res.data.success) {
        setOvertimes(prev => prev.map(o => (o.id === ov.id ? { ...o, status: 'REJECTED' } : o)));
        toast.success('특근이 반려되었습니다.');
        refreshInbox(user);
      }
    } catch {
      toast.error('처리 중 오류가 발생했습니다.');
    }
  };

  const tabs = [
    { key: 'leave', label: '연차', pending: requests.filter(r => r.appStatus === 'PENDING').length },
    { key: 'overtime', label: '특근', pending: overtimes.filter(r => r.status === 'PENDING').length },
  ];

  return (
    <>
      <PageHeader
        title="결재함"
        description={isChief
          ? '각 팀 팀장이 신청한 연차·특근을 최종 승인하거나 반려합니다.'
          : '소속 팀원이 신청한 연차·특근을 승인하거나 반려합니다.'}
      />

      {/* 통계 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="승인 대기" value={countBy('PENDING')} unit="건" icon={Hourglass} tone="amber" />
        <StatCard label="승인 완료" value={countBy('APPROVED')} unit="건" icon={CheckCircle2} tone="green" />
        <StatCard label="반려" value={countBy('REJECTED')} unit="건" icon={XCircle} tone="red" />
      </div>

      <Card className="mt-4 overflow-hidden">
        {/* 탭 */}
        <div className="flex gap-6 border-b border-gray-200 px-5">
          {tabs.map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              className={cn(
                '-mb-px flex h-12 items-center gap-2 border-b-2 text-sm transition-colors',
                tab === t.key ? 'border-blue-600 font-semibold text-gray-900' : 'border-transparent text-gray-500 hover:text-gray-800'
              )}
            >
              {t.label} 신청
              {t.pending > 0 && (
                <span className="rounded-full bg-amber-100 px-1.5 text-xs font-medium tabular-nums text-amber-800">{t.pending}</span>
              )}
            </button>
          ))}
        </div>

        {tab === 'leave' ? (
          <Table>
            <thead>
              <tr>
                <Th>신청자</Th>
                <Th>유형</Th>
                <Th>기간</Th>
                <Th>사유</Th>
                <Th>신청일</Th>
                <Th align="center">상태</Th>
                <Th align="right">결재</Th>
              </tr>
            </thead>
            <tbody>
              {requests.length > 0 ? (
                requests.map((req) => (
                  <Tr key={req.appId}>
                    <Td><RequesterCell name={req.requesterName} role={req.userRole} departmentCode={req.departmentCode} /></Td>
                    <Td><Badge tone="blue">{LEAVE_TYPE_LABELS[req.appType] || req.appType}</Badge></Td>
                    <Td className="whitespace-nowrap tabular-nums">
                      {req.startDate === req.endDate ? req.startDate : `${req.startDate} ~ ${req.endDate}`}
                      <span className="ml-1.5 text-xs text-gray-500">{req.usedDays}일</span>
                    </Td>
                    <Td className="max-w-[220px] truncate text-gray-500">{req.requestReason}</Td>
                    <Td className="tabular-nums text-gray-500">{req.createdAt?.split('T')[0]}</Td>
                    <Td align="center"><StatusBadge status={req.appStatus} /></Td>
                    <Td align="right">
                      {req.appStatus === 'PENDING' ? (
                        <ActionButtons
                          onApprove={() => handleApprove(req)}
                          onReject={() => { setSelectedRequest(req); setIsModalOpen(true); }}
                        />
                      ) : (
                        <span className="ml-auto block max-w-[180px] truncate text-xs text-gray-500">
                          {req.appStatus === 'REJECTED' ? `사유: ${req.rejectReason || '없음'}` : '처리 완료'}
                        </span>
                      )}
                    </Td>
                  </Tr>
                ))
              ) : (
                <TableEmpty colSpan={7}>결재할 연차 신청이 없습니다.</TableEmpty>
              )}
            </tbody>
          </Table>
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>신청자</Th>
                <Th>특근일</Th>
                <Th>시간</Th>
                <Th>사유</Th>
                <Th>신청일</Th>
                <Th align="center">상태</Th>
                <Th align="right">결재</Th>
              </tr>
            </thead>
            <tbody>
              {overtimes.length > 0 ? (
                overtimes.map((ov) => (
                  <Tr key={ov.id}>
                    <Td><RequesterCell name={ov.requesterName} role={ov.userRole} departmentCode={ov.departmentCode} /></Td>
                    <Td className="tabular-nums">{ov.overtimeDate}</Td>
                    <Td className="whitespace-nowrap tabular-nums">{ov.startTime?.slice(0, 5)} ~ {ov.endTime?.slice(0, 5)}</Td>
                    <Td className="max-w-[220px] truncate text-gray-500">{ov.reason}</Td>
                    <Td className="tabular-nums text-gray-500">{ov.createdAt?.split('T')[0]}</Td>
                    <Td align="center"><StatusBadge status={ov.status} /></Td>
                    <Td align="right">
                      {ov.status === 'PENDING' ? (
                        <ActionButtons onApprove={() => handleOvertimeApprove(ov)} onReject={() => handleOvertimeReject(ov)} />
                      ) : (
                        <span className="text-xs text-gray-500">처리 완료</span>
                      )}
                    </Td>
                  </Tr>
                ))
              ) : (
                <TableEmpty colSpan={7}>결재할 특근 신청이 없습니다.</TableEmpty>
              )}
            </tbody>
          </Table>
        )}
      </Card>

      {/* 반려 사유 입력 모달 */}
      <Modal
        open={isModalOpen}
        onClose={closeRejectModal}
        title="연차 반려"
        description={selectedRequest && `${selectedRequest.requesterName} · ${selectedRequest.startDate} ~ ${selectedRequest.endDate}`}
        footer={
          <>
            <Button variant="secondary" onClick={closeRejectModal}>취소</Button>
            <Button variant="danger" onClick={handleRejectSubmit}>반려</Button>
          </>
        }
      >
        <Field label="반려 사유" htmlFor="reject-reason" required hint="신청자에게 그대로 전달됩니다.">
          <Textarea
            id="reject-reason"
            rows={4}
            placeholder="반려 사유를 입력하세요"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            autoFocus
          />
        </Field>
      </Modal>
    </>
  );
};

export default LeaveApprovalPage;
