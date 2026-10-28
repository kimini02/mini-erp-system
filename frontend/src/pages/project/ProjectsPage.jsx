import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, FolderKanban, FolderPlus, Search, Users } from 'lucide-react';
import { projectApi } from '@/api/m4';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import { getErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/cn';
import { Badge, Button, Card, EmptyState, Input, PageHeader, PageLoader, Progress } from '@/components/ui';
import { formatPeriod } from '@/lib/project';
import { PersonAvatar, PriorityChip, ProjectFormModal, ProjectStatusBadge } from '@/components/project/ProjectBits';

const STATUS_FILTERS = [
  { value: '', label: '전체' },
  { value: 'PROGRESS', label: '진행 중' },
  { value: 'READY', label: '준비' },
  { value: 'DONE', label: '완료' },
];
const PAGE_SIZE = 12;

const SCOPE_NOTE = {
  SELF: '내가 팀장이거나 멤버인 프로젝트만 보입니다.',
  DEPT: '내 프로젝트와 우리 부서 팀장의 프로젝트가 보입니다.',
  DEPT_TREE: '내 프로젝트와 우리 부서·하위 부서 팀장의 프로젝트가 보입니다.',
  ALL: '회사의 모든 프로젝트가 보입니다.',
};

function ProjectCard({ p }) {
  return (
    <Card className="flex flex-col transition-shadow hover:shadow-sm">
      <Link to={`/projects/${p.id}/board`} className="flex flex-1 flex-col gap-3 p-5">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="flex items-center gap-1.5 text-xs text-gray-500">
              <span className="font-semibold tabular-nums text-gray-700">{p.key}</span>
              <PriorityChip priority={p.priority} />
              {p.myRole === 'LEADER' && <Badge tone="blue">담당</Badge>}
              {p.myRole === 'MEMBER' && <Badge>멤버</Badge>}
            </p>
            <h2 className="mt-1 truncate text-[15px] font-semibold text-gray-900">{p.title}</h2>
          </div>
          <ProjectStatusBadge status={p.status} />
        </div>
        <p className="text-xs tabular-nums text-gray-500">{formatPeriod(p)}</p>
        <div>
          <div className="flex justify-between text-[13px]">
            <span className="text-gray-600">완료 {p.doneCount} / 전체 {p.taskCount}{p.overdueCount > 0 && <span className="text-red-600"> · 지연 {p.overdueCount}</span>}</span>
            <span className="font-semibold tabular-nums text-gray-900">{p.progressPct}%</span>
          </div>
          <Progress value={p.progressPct} tone={p.status === 'DONE' ? 'green' : 'blue'} className="mt-1.5" />
        </div>
        <div className="mt-auto flex items-center justify-between border-t border-gray-100 pt-3 text-[13px] text-gray-600">
          <span className="flex items-center gap-2">
            <PersonAvatar person={p.leader} size="sm" />
            {p.leader?.name} {p.leader?.title}
          </span>
          <span className="flex items-center gap-1 text-gray-500"><Users size={14} /> {p.memberCount}명</span>
        </div>
      </Link>
    </Card>
  );
}

export default function ProjectsPage() {
  const navigate = useNavigate();
  const me = useAuthStore((s) => s.user);
  const canCreate = useAuthStore((s) => s.can(MENU.PROJECT, 'C'));
  const scope = useAuthStore((s) => s.scopeOf(MENU.PROJECT));
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);

  useEffect(() => setPage(0), [status, q]);

  const load = useCallback(async () => {
    try {
      setResult(await projectApi.list({ status: status || undefined, q: q.trim() || undefined, page, size: PAGE_SIZE }));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, [status, q, page]);

  useEffect(() => {
    const id = setTimeout(load, 200);
    return () => clearTimeout(id);
  }, [load]);

  const rows = result?.content || [];
  const info = result?.page;

  return (
    <>
      <PageHeader
        title="프로젝트"
        description={SCOPE_NOTE[scope] || '볼 수 있는 프로젝트 목록입니다.'}
        actions={canCreate && <Button icon={FolderPlus} onClick={() => setCreating(true)}>프로젝트 등록</Button>}
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <label className="w-full sm:w-64">
          <span className="sr-only">프로젝트 검색</span>
          <Input icon={Search} type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름 또는 키" />
        </label>
        <div role="group" aria-label="상태로 거르기" className="flex flex-wrap gap-1.5">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              aria-pressed={status === f.value}
              onClick={() => setStatus(f.value)}
              className={cn('h-8 rounded-full px-3 text-[13px] font-medium transition-colors', status === f.value ? 'bg-gray-900 text-white' : 'bg-white text-gray-600 ring-1 ring-gray-200 hover:bg-gray-50')}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {error ? <EmptyState icon={FolderKanban} title="프로젝트를 불러오지 못했습니다" description={error} /> : !result ? <PageLoader /> : rows.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title={q || status ? '조건에 맞는 프로젝트가 없습니다' : '아직 참여한 프로젝트가 없습니다'}
          description={canCreate ? '프로젝트를 등록하고 멤버를 추가해 보세요.' : '프로젝트 담당 팀장이 멤버로 추가하면 여기에 보입니다.'}
        />
      ) : (
        <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(min(100%,300px),1fr))]">
          {rows.map((p) => <ProjectCard key={p.id} p={p} />)}
        </div>
      )}

      {info && info.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-[13px] text-gray-500">
          <span>총 <b className="font-semibold text-gray-900">{info.totalElements}</b>개 · {info.number + 1} / {info.totalPages} 쪽</span>
          <div className="flex gap-1">
            <Button variant="secondary" size="sm" icon={ChevronLeft} disabled={info.first} onClick={() => setPage((p) => p - 1)} aria-label="이전 쪽" />
            <Button variant="secondary" size="sm" icon={ChevronRight} disabled={info.last} onClick={() => setPage((p) => p + 1)} aria-label="다음 쪽" />
          </div>
        </div>
      )}

      <ProjectFormModal open={creating} me={me} onClose={() => setCreating(false)} onSaved={(p) => { setCreating(false); navigate(`/projects/${p.id}/board`); }} />
    </>
  );
}
