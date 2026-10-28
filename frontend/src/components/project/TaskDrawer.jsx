import { useEffect, useState } from 'react';
import { Lock, Pencil, X } from 'lucide-react';
import { boardApi } from '@/api/project';
import { PRIORITIES, PRIORITY_META, TASK_STATUSES, TASK_STATUS_META, dueText, historySentence } from '@/lib/project';
import { formatDateTime } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Button, Field, Input, PageLoader, Select, Textarea } from '@/components/ui';
import { PersonAvatar, PriorityChip } from './ProjectBits';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
function dueLong(task) {
  if (!task.endDate) return '-';
  const d = new Date(`${task.endDate}T00:00:00`);
  const base = `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]})`;
  return task.overdueDays > 0 ? `${base} · ${task.overdueDays}일 지남` : base;
}

/**
 * 업무 상세 (시안 Kanban의 옆 패널)
 * - 상태는 담당자·관리자만 바꿈 (명세 4.3, 이동 API를 그대로 씀)
 * - 관리자는 제목·설명·마감일·우선순위·담당자를 고침 (명세 4.4)
 */
export default function TaskDrawer({ taskId, members, onClose, onMoveStatus, onChanged }) {
  const [task, setTask] = useState(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);

  const load = () =>
    boardApi
      .task(taskId)
      .then((t) => {
        setTask(t);
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  useEffect(() => {
    setTask(null);
    setEditing(false);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId]);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const startEdit = () => {
    setForm({
      title: task.title,
      content: task.content || '',
      endDate: task.endDate,
      priority: task.priority,
      assigneeIds: task.assignees.map((a) => a.id),
    });
    setEditing(true);
  };
  const toggleAssignee = (id) =>
    setForm((f) => ({
      ...f,
      assigneeIds: f.assigneeIds.includes(id) ? f.assigneeIds.filter((x) => x !== id) : [...f.assigneeIds, id],
    }));

  const save = async () => {
    setSaving(true);
    try {
      const next = await boardApi.updateTask(task.id, { ...form, title: form.title.trim() });
      setTask(next);
      setEditing(false);
      toast.success(`${next.key}를 고쳤습니다.`);
      onChanged();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (status) => {
    const moved = await onMoveStatus(task, status);
    if (moved) load();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-gray-900/25"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-drawer-title"
        className="flex h-full w-full max-w-[440px] flex-col overflow-y-auto bg-white shadow-[-12px_0_32px_rgba(17,24,39,0.12)]"
      >
        <div className="flex items-center gap-2 border-b border-gray-100 px-5 py-4">
          {task && <span className="text-[13px] tabular-nums text-gray-500">{task.key}</span>}
          {task && <PriorityChip priority={task.priority} />}
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="ml-auto flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100"
          >
            <X size={18} />
          </button>
        </div>

        {error ? (
          <p className="p-5 text-[13px] text-red-600">{error}</p>
        ) : !task ? (
          <PageLoader />
        ) : (
          <div className="flex flex-col gap-5 p-5">
            {editing ? (
              <Field label="제목" htmlFor="td-title" required>
                <Input
                  id="td-title"
                  value={form.title}
                  maxLength={100}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                />
              </Field>
            ) : (
              <h2
                id="task-drawer-title"
                className={cn(
                  'text-lg font-semibold leading-snug',
                  task.status === 'DONE' ? 'text-gray-500 line-through' : 'text-gray-900',
                )}
              >
                {task.title}
              </h2>
            )}

            <dl className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-y-3.5 text-sm">
              <dt className="text-[13px] text-gray-500">상태</dt>
              <dd>
                <label htmlFor="td-status" className="sr-only">
                  상태 변경
                </label>
                <Select
                  id="td-status"
                  value={task.status}
                  disabled={!task.canMove || editing}
                  onChange={(e) => changeStatus(e.target.value)}
                  className="w-40"
                >
                  {TASK_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {TASK_STATUS_META[s].label}
                    </option>
                  ))}
                </Select>
                {!task.canMove && (
                  <p className="mt-1.5 flex items-center gap-1 text-xs text-gray-500">
                    <Lock size={12} /> 담당자와 프로젝트 담당 팀장만 상태를 바꿀 수 있습니다.
                  </p>
                )}
              </dd>

              <dt className="self-start pt-1 text-[13px] text-gray-500">담당자</dt>
              <dd>
                {editing ? (
                  <div className="flex flex-wrap gap-1.5">
                    {members.map((m) => {
                      const on = form.assigneeIds.includes(m.id);
                      return (
                        <button
                          key={m.id}
                          type="button"
                          aria-pressed={on}
                          onClick={() => toggleAssignee(m.id)}
                          className={cn(
                            'flex items-center gap-1.5 rounded-full border py-0.5 pl-0.5 pr-2.5 text-[13px]',
                            on
                              ? 'border-blue-600 bg-blue-50 text-blue-800'
                              : 'border-gray-200 text-gray-600 hover:bg-gray-50',
                          )}
                        >
                          <PersonAvatar person={m} size="sm" /> {m.name}
                        </button>
                      );
                    })}
                  </div>
                ) : task.assignees.length === 0 ? (
                  <span className="text-gray-400">없음</span>
                ) : (
                  <div className="flex flex-col gap-1.5">
                    {task.assignees.map((a) => (
                      <span key={a.id} className="flex items-center gap-2">
                        <PersonAvatar person={a} size="sm" /> {a.name} <span className="text-gray-500">{a.title}</span>
                      </span>
                    ))}
                  </div>
                )}
              </dd>

              <dt className="text-[13px] text-gray-500">마감일</dt>
              <dd>
                {editing ? (
                  <Input
                    type="date"
                    value={form.endDate}
                    onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                    className="w-44"
                    aria-label="마감일"
                  />
                ) : (
                  <span className={cn(dueText(task).late ? 'font-semibold text-red-600' : 'text-gray-900')}>
                    {dueLong(task)}
                  </span>
                )}
              </dd>

              {editing && (
                <>
                  <dt className="text-[13px] text-gray-500">우선순위</dt>
                  <dd>
                    <Select
                      value={form.priority}
                      onChange={(e) => setForm({ ...form, priority: e.target.value })}
                      className="w-32"
                      aria-label="우선순위"
                    >
                      {PRIORITIES.map((p) => (
                        <option key={p} value={p}>
                          {PRIORITY_META[p].label}
                        </option>
                      ))}
                    </Select>
                  </dd>
                </>
              )}
            </dl>

            <div>
              <h3 className="mb-1.5 text-[13px] font-semibold text-gray-700">설명</h3>
              {editing ? (
                <Textarea
                  rows={5}
                  value={form.content}
                  maxLength={1000}
                  onChange={(e) => setForm({ ...form, content: e.target.value })}
                  aria-label="설명"
                />
              ) : (
                <p className="whitespace-pre-line text-sm text-gray-700">
                  {task.content || <span className="text-gray-400">설명이 없습니다.</span>}
                </p>
              )}
            </div>

            {task.canManage && (
              <div className="flex justify-end gap-2">
                {editing ? (
                  <>
                    <Button variant="secondary" onClick={() => setEditing(false)}>
                      취소
                    </Button>
                    <Button loading={saving} disabled={!form.title.trim()} onClick={save}>
                      저장
                    </Button>
                  </>
                ) : (
                  <Button variant="secondary" icon={Pencil} onClick={startEdit}>
                    수정
                  </Button>
                )}
              </div>
            )}

            <div>
              <h3 className="mb-2 text-[13px] font-semibold text-gray-700">변경 이력</h3>
              <ol className="flex flex-col gap-2.5">
                {task.history.map((h, i) => (
                  <li key={i} className="flex gap-2.5 text-[13px]">
                    <span className="mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full bg-gray-300" />
                    <span className="leading-snug">
                      <span className="text-gray-900">{historySentence(h)}</span>
                      <span className="block text-xs tabular-nums text-gray-400">{formatDateTime(h.at)}</span>
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
