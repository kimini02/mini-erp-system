import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Users, ListChecks, ChevronRight, CalendarRange } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { getRoleInfo } from '@/lib/roles';
import { Badge, Button, PageHeader, Progress } from '@/components/ui';

// 테스트용 데이터
const projects = [
  { id: 1, title: '사내 그룹웨어 개발', period: '2025.02.01 - 2025.04.30', progress: 65, status: '진행중', members: 6, tasks: 20 },
  { id: 2, title: 'ERP 시스템 고도화', period: '2025.03.01 - 2025.06.30', progress: 30, status: '검토중', members: 4, tasks: 20 },
  { id: 3, title: '모바일 앱 리뉴얼', period: '2025.04.01 - 2025.09.30', progress: 10, status: '기획중', members: 5, tasks: 20 },
  { id: 4, title: '사내 인트라넷 개선', period: '2024.10.01 - 2025.02.28', progress: 100, status: '완료', members: 3, tasks: 15 },
];

const STATUS_TONE = { 진행중: 'blue', 검토중: 'amber', 기획중: 'gray', 완료: 'green' };

const ProjectManagement = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const { basePath } = getRoleInfo(user);
  const goCreate = () => navigate(`${basePath}/projects/new`);

  return (
    <>
      <PageHeader
        title="프로젝트 관리"
        description="전체 프로젝트를 만들고 진행 상황을 관리합니다."
        actions={<Button icon={Plus} onClick={goCreate}>새 프로젝트</Button>}
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {projects.map((project) => (
          <ProjectCard key={project.id} {...project} />
        ))}

        {/* 새 프로젝트 생성 카드 */}
        <button
          type="button"
          onClick={goCreate}
          className="flex min-h-[208px] flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-gray-300 text-gray-500 transition-colors hover:border-blue-400 hover:bg-blue-50/40 hover:text-blue-600"
        >
          <Plus size={20} />
          <span className="text-sm font-medium">새 프로젝트</span>
        </button>
      </div>
    </>
  );
};

/**
 * 개별 프로젝트 카드
 */
const ProjectCard = ({ title, period, progress, status, members, tasks }) => (
  <div className="flex flex-col rounded-xl border border-gray-200 bg-white p-5 transition-colors hover:border-gray-300">
    <div className="flex items-start justify-between gap-3">
      <h3 className="text-[15px] font-semibold leading-snug text-gray-900">{title}</h3>
      <Badge tone={STATUS_TONE[status]}>{status}</Badge>
    </div>
    <p className="mt-1.5 flex items-center gap-1.5 text-xs text-gray-500">
      <CalendarRange size={13} /> {period}
    </p>

    <div className="mt-5">
      <div className="mb-1.5 flex justify-between text-xs">
        <span className="text-gray-500">진행률</span>
        <span className="font-medium tabular-nums text-gray-900">{progress}%</span>
      </div>
      <Progress value={progress} tone={progress === 100 ? 'green' : 'blue'} />
    </div>

    <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
      <div className="flex items-center gap-3 text-xs text-gray-500">
        <span className="flex items-center gap-1"><Users size={13} /> {members}명</span>
        <span className="flex items-center gap-1"><ListChecks size={13} /> 업무 {tasks}건</span>
      </div>
      <button type="button" className="flex items-center gap-0.5 text-xs font-medium text-gray-500 hover:text-gray-900">
        상세 <ChevronRight size={14} />
      </button>
    </div>
  </div>
);

export default ProjectManagement;
