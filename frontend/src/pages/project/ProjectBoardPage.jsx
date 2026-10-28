import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, CalendarClock, FolderKanban, Lock, Plus, Search, Settings2, Users } from 'lucide-react';
import { boardApi } from '@/api/m4';
import { useAuthStore } from '@/store/authStore';
import { TASK_STATUS_META, dueText, formatPeriod, toParticle } from '@/lib/project';
import { getErrorCode, getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Button, EmptyState, Input, PageLoader } from '@/components/ui';
import {
  MembersModal,
  PersonAvatar,
  PriorityChip,
  ProjectFormModal,
  ProjectStatusBadge,
} from '@/components/project/ProjectBits';
import TaskDrawer from '@/components/project/TaskDrawer';

/** 카드 한 장 (시안 Kanban) */
function TaskCard({ task, dragging, onOpen, onDragStart, onDragEnd, onDragOver }) {
  const due = dueText(task);
  return (
    <div
      draggable={task.canMove}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragOver={onDragOver}
      className={cn(
        'rounded-[10px] border bg-white shadow-[0_1px_2px_rgba(17,24,39,0.05)]',
        dragging ? 'border-blue-300 opacity-40' : 'border-gray-200',
        task.canMove ? 'cursor-grab active:cursor-grabbing' : 'cursor-pointer',
      )}
    >
      <button
        type="button"
        onClick={onOpen}
        aria-label={`${task.key} ${task.title} 열기`}
        className="flex w-full flex-col gap-2 p-3 text-left text-sm text-gray-900"
        style={{ cursor: 'inherit' }}
      >
        <span className="flex items-center gap-1.5">
          <span className="text-xs tabular-nums text-gray-500">{task.key}</span>
          <PriorityChip priority={task.priority} />
          {!task.canMove && <Lock size={12} className="text-gray-400" aria-label="옮길 수 없음" />}
        </span>
        <span className={cn('font-medium leading-snug', task.status === 'DONE' && 'text-gray-500 line-through')}>
          {task.title}
        </span>
        <span className="flex items-center justify-between gap-2">
          <span
            className={cn(
              'inline-flex items-center gap-1 text-xs',
              due.late ? 'font-semibold text-red-600' : 'text-gray-500',
            )}
          >
            <CalendarClock size={13} /> {due.text}
          </span>
          <span className="flex -space-x-1.5">
            {task.assignees.length === 0 ? (
              <span className="text-xs text-gray-400">담당 없음</span>
            ) : (
              task.assignees
                .slice(0, 3)
                .map((a) => <PersonAvatar key={a.id} person={a} size="sm" className="ring-2 ring-white" />)
            )}
          </span>
        </span>
        {task.assignees.length > 3 && (
          <span className="text-right text-[11px] text-gray-400">외 {task.assignees.length - 3}명</span>
        )}
      </button>
    </div>
  );
}

const DropLine = () => <div className="h-1 rounded-full bg-blue-500" aria-hidden="true" />;

export default function ProjectBoardPage() {
  const { id } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const me = useAuthStore((s) => s.user);
  const [board, setBoard] = useState(null);
  const [error, setError] = useState('');
  const [who, setWho] = useState(null);
  const [mine, setMine] = useState(false);
  const [q, setQ] = useState('');
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState('');
  const [drag, setDrag] = useState(null); // { id, status }
  const [over, setOver] = useState(null); // { status, index }
  const [editingProject, setEditingProject] = useState(false);
  const [editingMembers, setEditingMembers] = useState(false);
  const openTaskId = searchParams.get('taskId');

  const load = useCallback(
    () =>
      boardApi
        .board(id)
        .then((b) => {
          setBoard(b);
          setError('');
        })
        .catch((err) => setError(getErrorMessage(err))),
    [id],
  );
  useEffect(() => {
    load();
  }, [load]);

  const filtered = Boolean(who || mine || q.trim());
  const matches = useCallback(
    (t) => {
      if (who && !t.assignees.some((a) => a.id === who)) return false;
      if (mine && !t.assignees.some((a) => a.id === me?.id)) return false;
      const s = q.trim().toLowerCase();
      return !s || t.title.toLowerCase().includes(s) || t.key.toLowerCase().includes(s);
    },
    [who, mine, q, me],
  );

  const columns = useMemo(
    () => (board?.columns || []).map((c) => ({ ...c, visible: c.tasks.filter(matches) })),
    [board, matches],
  );
  const allTasks = useMemo(() => columns.flatMap((c) => c.tasks), [columns]);

  /** 화면을 먼저 바꾸고(낙관적) 서버 결과로 맞춤. 409면 최신 카드로 바꿔 그리고 다시 불러옴 */
  const move = useCallback(
    async (task, status, afterTaskId) => {
      setBoard((b) => {
        const cols = b.columns.map((c) => ({ ...c, tasks: c.tasks.filter((t) => t.id !== task.id) }));
        const target = cols.find((c) => c.status === status);
        const at = afterTaskId == null ? 0 : target.tasks.findIndex((t) => t.id === afterTaskId) + 1;
        target.tasks.splice(at, 0, { ...task, status });
        return { ...b, columns: cols };
      });
      try {
        const res = await boardApi.move(task.id, { status, afterTaskId, version: task.version });
        setBoard((b) => ({
          ...b,
          project: { ...b.project, ...res.project },
          columns: b.columns.map((c) => ({ ...c, tasks: c.tasks.map((t) => (t.id === res.task.id ? res.task : t)) })),
        }));
        const label = TASK_STATUS_META[status].label;
        if (status !== task.status) toast.success(`${task.key}를 '${label}'${toParticle(label)} 옮겼습니다.`);
        return true;
      } catch (err) {
        toast.error(
          getErrorCode(err) === 'TASK_VERSION_CONFLICT'
            ? '다른 사람이 먼저 이 업무를 옮겼습니다. 보드를 최신으로 바꿨습니다.'
            : getErrorMessage(err),
        );
        load();
        return false;
      }
    },
    [load],
  );

  const visibleIn = (status) => columns.find((c) => c.status === status).visible.filter((t) => t.id !== drag?.id);

  const onCardDragOver = (status, index) => (e) => {
    if (!drag) return;
    e.preventDefault();
    e.stopPropagation();
    const rect = e.currentTarget.getBoundingClientRect();
    const next = { status, index: e.clientY < rect.top + rect.height / 2 ? index : index + 1 };
    if (over?.status !== next.status || over?.index !== next.index) setOver(next);
  };
  const onColumnDragOver = (status) => (e) => {
    if (!drag) return;
    e.preventDefault();
    if (e.target === e.currentTarget && (over?.status !== status || over?.index !== visibleIn(status).length)) {
      setOver({ status, index: visibleIn(status).length });
    }
  };
  const onDrop = (status) => (e) => {
    e.preventDefault();
    const task = allTasks.find((t) => t.id === drag?.id);
    const target = over?.status === status ? over : { status, index: visibleIn(status).length };
    setDrag(null);
    setOver(null);
    if (!task) return;
    const list = visibleIn(status);
    const afterTaskId = target.index > 0 ? list[target.index - 1].id : null;
    // 같은 자리에 놓으면 아무것도 하지 않음
    const full = columns.find((c) => c.status === status).tasks;
    const currentIndex = full.findIndex((t) => t.id === task.id);
    if (task.status === status && currentIndex >= 0) {
      const before = full[currentIndex - 1]?.id ?? null;
      if (before === afterTaskId) return;
    }
    move(task, status, afterTaskId);
  };

  /** 상세 패널의 상태 바꾸기: 그 열 맨 아래로 */
  const moveStatusFromDrawer = (task, status) => {
    const target = columns.find((c) => c.status === status).tasks.filter((t) => t.id !== task.id);
    const card = allTasks.find((t) => t.id === task.id) || task;
    return move({ ...card, version: task.version }, status, target.length ? target[target.length - 1].id : null);
  };

  const addTask = async () => {
    const title = draft.trim();
    if (!title) return;
    try {
      const created = await boardApi.createTask(id, { title, status: 'TODO' });
      setBoard((b) => ({
        ...b,
        project: {
          ...b.project,
          taskCount: b.project.taskCount + 1,
          progressPct: Math.floor((b.project.doneCount * 100) / (b.project.taskCount + 1)),
        },
        columns: b.columns.map((c) => (c.status === 'TODO' ? { ...c, tasks: [...c.tasks, created] } : c)),
      }));
      setDraft('');
      toast.success(`${created.key}를 추가했습니다. 카드를 눌러 담당자와 마감일을 정하세요.`);
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  const openTask = (taskId) =>
    setSearchParams((p) => {
      const next = new URLSearchParams(p);
      next.set('taskId', taskId);
      return next;
    });
  const closeTask = useCallback(
    () =>
      setSearchParams((p) => {
        const next = new URLSearchParams(p);
        next.delete('taskId');
        return next;
      }),
    [setSearchParams],
  );

  if (error && !board) {
    return (
      <EmptyState
        icon={FolderKanban}
        title="보드를 열 수 없습니다"
        description={error}
        action={
          <Link to="/projects">
            <Button variant="secondary" icon={ArrowLeft}>
              프로젝트 목록
            </Button>
          </Link>
        }
      />
    );
  }
  if (!board) return <PageLoader />;

  const { project, members, permissions } = board;
  const isLeader = project.leader?.id === me?.id;
  const permNote = permissions.canManage
    ? `${isLeader ? '프로젝트 담당 팀장' : '프로젝트 관리 권한'} · 모든 업무를 옮길 수 있습니다`
    : '내가 담당한 업무만 옮길 수 있습니다';

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <Link
            to="/projects"
            className="mb-1 inline-flex items-center gap-1 text-[13px] text-gray-500 hover:text-gray-800"
          >
            <ArrowLeft size={14} /> 프로젝트
          </Link>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-[22px] font-semibold tracking-tight text-gray-900">{project.title}</h1>
            <ProjectStatusBadge status={project.status} />
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-4 text-[13px] text-gray-500">
            <span className="tabular-nums">{formatPeriod(project)}</span>
            <span>
              담당 {project.leader?.name} {project.leader?.title}
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="h-1.5 w-[120px] overflow-hidden rounded-full bg-gray-200">
                <span
                  className="block h-full bg-blue-600 transition-[width]"
                  style={{ width: `${project.progressPct}%` }}
                />
              </span>
              <b className="font-semibold tabular-nums text-gray-900">{project.progressPct}%</b>
              <span className="tabular-nums">
                ({project.doneCount}/{project.taskCount})
              </span>
            </span>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {permissions.canManage && (
            <Button variant="secondary" icon={Users} onClick={() => setEditingMembers(true)}>
              멤버 {members.length}
            </Button>
          )}
          {permissions.canManage && (
            <Button variant="secondary" icon={Settings2} onClick={() => setEditingProject(true)}>
              설정
            </Button>
          )}
          {permissions.canCreateTask && (
            <Button
              icon={Plus}
              onClick={() => {
                setAdding(true);
                setDraft('');
              }}
            >
              업무 추가
            </Button>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <label className="w-full sm:w-[260px]">
          <span className="sr-only">업무 검색</span>
          <Input
            icon={Search}
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="업무 제목, 번호 검색"
          />
        </label>
        <div role="group" aria-label="담당자로 거르기" className="flex items-center pl-1.5">
          {members.map((m) => {
            const pressed = who === m.id;
            return (
              <button
                key={m.id}
                type="button"
                aria-pressed={pressed}
                aria-label={`${m.name} 업무만 보기`}
                title={m.name}
                onClick={() => setWho(pressed ? null : m.id)}
                className={cn(
                  '-ml-1.5 rounded-full border-2 transition-opacity',
                  pressed ? 'border-blue-600' : 'border-white',
                  who && !pressed && 'opacity-40',
                )}
              >
                <PersonAvatar person={m} />
              </button>
            );
          })}
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={mine}
          onClick={() => setMine((v) => !v)}
          className={cn(
            'h-9 rounded-full border px-3 text-[13px] font-medium',
            mine
              ? 'border-blue-600 bg-blue-50 text-blue-700'
              : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50',
          )}
        >
          내 업무만
        </button>
        {filtered && (
          <button
            type="button"
            onClick={() => {
              setWho(null);
              setMine(false);
              setQ('');
            }}
            className="h-9 px-2 text-[13px] font-medium text-blue-700 hover:underline"
          >
            필터 지우기
          </button>
        )}
        <span className="ml-auto text-xs text-gray-500">{permNote}</span>
      </div>

      <div className="grid items-start gap-4 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        {columns.map((col) => {
          const meta = TASK_STATUS_META[col.status];
          const list = col.visible.filter((t) => t.id !== drag?.id);
          const isOver = drag && over?.status === col.status;
          return (
            <section
              key={col.status}
              aria-label={`${meta.label} ${col.visible.length}건`}
              onDragOver={onColumnDragOver(col.status)}
              onDrop={onDrop(col.status)}
              className={cn(
                'flex min-h-[560px] flex-col gap-2.5 rounded-xl border-2 border-dashed p-3',
                meta.column,
                isOver ? 'border-blue-300' : 'border-transparent',
              )}
            >
              <div className="flex items-center gap-2 px-1 pb-1 pt-0.5">
                <span className={cn('h-2 w-2 rounded-full', meta.dot)} />
                <h2 className="text-sm font-semibold text-gray-900">{meta.label}</h2>
                <span className="text-[13px] tabular-nums text-gray-500">
                  {filtered ? `${col.visible.length} / ${col.tasks.length}` : col.tasks.length}
                </span>
              </div>

              {col.status === 'TODO' && adding && (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    addTask();
                  }}
                  className="flex flex-col gap-2 rounded-[10px] border border-blue-300 bg-white p-2.5"
                >
                  <label htmlFor="new-task" className="sr-only">
                    새 업무 제목
                  </label>
                  <Input
                    id="new-task"
                    autoFocus
                    value={draft}
                    maxLength={100}
                    onChange={(e) => setDraft(e.target.value)}
                    placeholder="업무 제목"
                    onKeyDown={(e) => e.key === 'Escape' && setAdding(false)}
                  />
                  <div className="flex justify-end gap-1.5">
                    <Button type="button" size="sm" variant="secondary" onClick={() => setAdding(false)}>
                      닫기
                    </Button>
                    <Button type="submit" size="sm" disabled={!draft.trim()}>
                      추가
                    </Button>
                  </div>
                </form>
              )}

              {/* 끄는 카드는 제자리에 흐리게 남김 (끌기 시작할 때 DOM에서 빼면 브라우저가 끌기를 취소함). 놓을 위치 번호는 끄는 카드를 뺀 순서 */}
              {col.visible.map((t) => {
                const isDragged = drag?.id === t.id;
                const index = isDragged ? -1 : list.findIndex((x) => x.id === t.id);
                return (
                  <Fragment key={t.id}>
                    {isOver && over.index === index && <DropLine />}
                    <TaskCard
                      task={t}
                      dragging={isDragged}
                      onOpen={() => openTask(t.id)}
                      onDragStart={(e) => {
                        e.dataTransfer.effectAllowed = 'move';
                        e.dataTransfer.setData('text/plain', String(t.id));
                        setDrag({ id: t.id, status: t.status });
                      }}
                      onDragEnd={() => {
                        setDrag(null);
                        setOver(null);
                      }}
                      onDragOver={isDragged ? undefined : onCardDragOver(col.status, index)}
                    />
                  </Fragment>
                );
              })}
              {isOver && over.index === list.length && <DropLine />}
              {col.visible.length === 0 && !(col.status === 'TODO' && adding) && (
                <p className="px-2 py-6 text-center text-[13px] text-gray-400">
                  {filtered ? '조건에 맞는 업무가 없습니다.' : meta.empty}
                </p>
              )}
            </section>
          );
        })}
      </div>

      {openTaskId && (
        <TaskDrawer
          taskId={openTaskId}
          members={members}
          onClose={closeTask}
          onMoveStatus={moveStatusFromDrawer}
          onChanged={load}
        />
      )}
      <ProjectFormModal
        open={editingProject}
        project={project}
        me={me}
        onClose={() => setEditingProject(false)}
        onSaved={() => {
          setEditingProject(false);
          load();
        }}
      />
      <MembersModal
        open={editingMembers}
        project={project}
        members={members}
        onClose={() => setEditingMembers(false)}
        onSaved={() => {
          setEditingMembers(false);
          load();
        }}
      />
    </div>
  );
}
