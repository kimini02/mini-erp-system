import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users, FolderKanban, FileClock, ListChecks, ChevronRight, Inbox } from 'lucide-react';
import api from '@/api/axios';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { getRoleInfo, getDisplayName } from '@/lib/roles';
import { LEAVE_TYPE_LABELS, ROLE_LABELS } from '@/lib/labels';
import { cn } from '@/lib/cn';
import {
  Avatar,
  Badge,
  Button,
  Card,
  CardHeader,
  CardBody,
  EmptyState,
  PageHeader,
  Progress,
  StatCard,
} from '@/components/ui';

// 프로젝트 현황 (API 연동 전 표시용 데이터)
const PROJECTS = [
  { label: '사내 그룹웨어 개발', percent: 65, due: '04.30', status: '진행중' },
  { label: 'ERP 시스템 고도화', percent: 30, due: '06.30', status: '검토중' },
  { label: '모바일 앱 리뉴얼', percent: 15, due: '09.30', status: '기획중' },
];

const AdminDashboard = () => {
  const { user } = useAuthStore();
  const navigate = useNavigate();
  const { leave: pendingLeave, overtime: pendingOvertime } = useApprovalStore();
  const { isChief, basePath } = getRoleInfo(user);

  const [taskStats, setTaskStats] = useState({
    todo: 0, inProgress: 0, done: 0, delayed: 0, total: 0, completionRate: 0
  });

  // Task 통계 (관리소장 전용)
  useEffect(() => {
    if (!isChief) return;
    const fetchTasks = async () => {
      try {
        const res = await api.get('/tasks');
        const allTasks = res.data?.data || (Array.isArray(res.data) ? res.data : []);
        const stats = {
          todo: allTasks.filter(t => ['TODO', '대기'].includes(t.status)).length,
          inProgress: allTasks.filter(t => ['IN_PROGRESS', '진행중'].includes(t.status)).length,
          done: allTasks.filter(t => ['DONE', '완료'].includes(t.status)).length,
          delayed: allTasks.filter(t => ['DELAYED', '지연'].includes(t.status)).length,
          total: allTasks.length
        };
        const rate = stats.total > 0 ? Math.round((stats.done / stats.total) * 100) : 0;
        setTaskStats({ ...stats, completionRate: rate });
      } catch (error) {
        console.error('Task 통계 로드 실패:', error);
      }
    };
    fetchTasks();
  }, [isChief]);

  const goApprovals = () => navigate(`${basePath}/approvals`);

  // 연차 + 특근 대기 건을 한 목록으로
  const pendingItems = [
    ...pendingLeave.map((req) => ({
      key: `lv-${req.appId}`,
      name: req.requesterName,
      role: req.userRole,
      type: LEAVE_TYPE_LABELS[req.appType] || req.appType,
      date: req.endDate && req.endDate !== req.startDate ? `${req.startDate} ~ ${req.endDate}` : req.startDate,
      reason: req.requestReason,
    })),
    ...pendingOvertime.map((req) => ({
      key: `ot-${req.id}`,
      name: req.requesterName,
      role: req.userRole,
      type: '특근',
      date: `${req.overtimeDate} ${req.startTime?.slice(0, 5) ?? ''}~${req.endTime?.slice(0, 5) ?? ''}`,
      reason: req.reason,
    })),
  ];

  const taskBlocks = [
    { label: '대기', value: taskStats.todo, dot: 'bg-gray-400' },
    { label: '진행중', value: taskStats.inProgress, dot: 'bg-blue-500' },
    { label: '완료', value: taskStats.done, dot: 'bg-emerald-500' },
    { label: '지연', value: taskStats.delayed, dot: 'bg-red-500' },
  ];

  return (
    <>
      <PageHeader
        title={`안녕하세요, ${getDisplayName(user)}님`}
        description={isChief ? '팀장들이 올린 결재 요청과 전사 업무 현황입니다.' : '팀원들이 올린 결재 요청과 팀 업무 현황입니다.'}
        actions={
          <Button variant="secondary" icon={FileClock} onClick={goApprovals}>
            결재함
          </Button>
        }
      />

      {/* 요약 지표 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="전체 사용자" value="24" unit="명" hint="이번 달 +2명" icon={Users} />
        <StatCard label="진행 중 프로젝트" value="8" unit="개" hint="전체 12개 프로젝트" icon={FolderKanban} />
        <StatCard
          label={isChief ? '팀장 결재 대기' : '팀원 결재 대기'}
          value={pendingItems.length}
          unit="건"
          hint={pendingItems.length > 0 ? '처리가 필요한 요청이 있습니다' : '모두 처리했습니다'}
          icon={FileClock}
          tone={pendingItems.length > 0 ? 'amber' : 'gray'}
        />
        <StatCard
          label="전체 업무"
          value={isChief ? taskStats.total : '-'}
          unit={isChief ? '건' : undefined}
          hint={isChief ? `완료율 ${taskStats.completionRate}%` : '관리소장 전용 지표'}
          icon={ListChecks}
        />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-12">
        {/* 결재 대기 목록 */}
        <Card className="lg:col-span-7">
          <CardHeader
            title="결재 대기"
            description={isChief ? '팀장 연차·특근 신청' : '팀원 연차·특근 신청'}
            action={
              <Button variant="ghost" size="sm" onClick={goApprovals}>
                전체보기 <ChevronRight size={14} />
              </Button>
            }
          />
          {pendingItems.length > 0 ? (
            <ul className="divide-y divide-gray-100">
              {pendingItems.slice(0, 5).map((item) => (
                <li key={item.key}>
                  <button type="button" onClick={goApprovals} className="flex w-full items-center gap-3 px-5 py-3.5 text-left hover:bg-gray-50">
                    <Avatar name={item.name} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">{item.name}</span>
                        <span className="text-xs text-gray-500">{ROLE_LABELS[item.role] || ''}</span>
                        <Badge tone={item.type === '특근' ? 'gray' : 'blue'}>{item.type}</Badge>
                      </div>
                      <p className="mt-0.5 truncate text-[13px] text-gray-500">
                        {item.date} · {item.reason || '개인 사유'}
                      </p>
                    </div>
                    <Badge tone="amber" dot>대기</Badge>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState icon={Inbox} title="대기 중인 결재가 없습니다" description="새로운 신청이 들어오면 여기에 표시됩니다." />
          )}
        </Card>

        {/* 프로젝트 현황 */}
        <Card className="lg:col-span-5">
          <CardHeader
            title="프로젝트 현황"
            action={
              <Button variant="ghost" size="sm" onClick={() => navigate(`${basePath}/projects`)}>
                전체보기 <ChevronRight size={14} />
              </Button>
            }
          />
          <CardBody className="space-y-5">
            {PROJECTS.map((p) => (
              <div key={p.label}>
                <div className="mb-2 flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-medium text-gray-900">{p.label}</span>
                  <Badge>{p.status}</Badge>
                </div>
                <Progress value={p.percent} />
                <div className="mt-1.5 flex justify-between text-xs text-gray-500">
                  <span className="tabular-nums">{p.percent}%</span>
                  <span>마감 {p.due}</span>
                </div>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>

      {/* 업무 상태 (관리소장 전용) */}
      {isChief && (
        <Card className="mt-4">
          <CardHeader title="업무 상태" description="전체 Task 기준" />
          <CardBody>
            <div className="grid grid-cols-1 items-center gap-6 md:grid-cols-12">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 md:col-span-8">
                {taskBlocks.map((b) => (
                  <div key={b.label} className="rounded-lg bg-gray-50 px-4 py-3">
                    <p className="flex items-center gap-1.5 text-[13px] text-gray-500">
                      <span className={cn('h-2 w-2 rounded-full', b.dot)} />
                      {b.label}
                    </p>
                    <p className="mt-1 text-xl font-semibold tabular-nums text-gray-900">{b.value}</p>
                  </div>
                ))}
              </div>
              <div className="md:col-span-4 md:border-l md:border-gray-100 md:pl-6">
                <div className="flex items-baseline justify-between">
                  <p className="text-[13px] text-gray-500">전체 완료율</p>
                  <p className="text-2xl font-semibold tabular-nums text-gray-900">{taskStats.completionRate}%</p>
                </div>
                <Progress value={taskStats.completionRate} tone="green" className="mt-3 h-2" />
              </div>
            </div>
          </CardBody>
        </Card>
      )}
    </>
  );
};

export default AdminDashboard;
