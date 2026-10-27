import { useEffect, useMemo, useState } from 'react';
import { Check, Search } from 'lucide-react';
import { userLookupApi } from '@/api/m2';
import { projectApi } from '@/api/m4';
import { PRIORITIES, PRIORITY_META, PROJECT_STATUS_META, avatarColor, shortName } from '@/lib/project';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Badge, Button, Field, Input, Modal, Select, Textarea } from '@/components/ui';

/** 시안의 원형 머리글자 (이름 뒤 두 글자, 사람마다 같은 색) */
export function PersonAvatar({ person, size = 'md', className }) {
  if (!person) return null;
  const sizes = { sm: 'h-[26px] w-[26px] text-[11px]', md: 'h-9 w-9 text-[11px]' };
  return (
    <span title={person.name} className={cn('inline-flex shrink-0 items-center justify-center rounded-full font-semibold', sizes[size], avatarColor(person.id), className)}>
      {shortName(person.name)}
    </span>
  );
}

export function PriorityChip({ priority, className }) {
  const meta = PRIORITY_META[priority] || PRIORITY_META.MEDIUM;
  return <span className={cn('rounded-md px-1.5 text-[11px] font-semibold', meta.chip, className)}>{meta.label}</span>;
}

export function ProjectStatusBadge({ status }) {
  const meta = PROJECT_STATUS_META[status] || { label: status, tone: 'gray' };
  return <Badge tone={meta.tone} dot>{meta.label}</Badge>;
}

/** 프로젝트 등록·수정 (명세 3장). 수정할 때 키는 바꾸지 않습니다. */
export function ProjectFormModal({ open, project, me, onClose, onSaved }) {
  const editing = Boolean(project);
  const [form, setForm] = useState(null);
  const [people, setPeople] = useState([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setError('');
    setForm(editing
      ? { key: project.key, title: project.title, content: project.content || '', startDate: project.startDate, endDate: project.endDate, priority: project.priority, leaderId: String(project.leader?.id || '') }
      : { key: '', title: '', content: '', startDate: new Date().toLocaleDateString('sv-SE'), endDate: '', priority: 'MEDIUM', leaderId: String(me?.id || '') });
    userLookupApi.search({ size: 100 }).then(setPeople).catch(() => setPeople([]));
  }, [open, editing, project, me]);

  if (!open || !form) return null;
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const keyOk = editing || /^[A-Z0-9]{2,10}$/.test(form.key);
  const valid = keyOk && form.title.trim() && form.startDate && form.endDate && form.endDate >= form.startDate && form.leaderId;

  const save = async () => {
    setSaving(true);
    setError('');
    try {
      const body = { title: form.title.trim(), content: form.content, startDate: form.startDate, endDate: form.endDate, priority: form.priority, leaderId: Number(form.leaderId) };
      const saved = editing ? await projectApi.update(project.id, body) : await projectApi.create({ ...body, key: form.key });
      toast.success(editing ? '프로젝트를 고쳤습니다.' : `${saved.key} 프로젝트를 만들었습니다. 보드에서 멤버와 업무를 추가하세요.`);
      onSaved(saved);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? '프로젝트 수정' : '프로젝트 등록'}
      description={editing ? '프로젝트 키는 업무 번호에 쓰여서 바꿀 수 없습니다.' : '키는 업무 번호 앞에 붙습니다 (예: GW → GW-21). 등록 뒤에는 바꿀 수 없습니다.'}
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button loading={saving} disabled={!valid} onClick={save}>{editing ? '저장' : '등록'}</Button></>}
    >
      <div className="space-y-4">
        <div className="grid grid-cols-[120px_1fr] gap-3">
          <Field label="키" htmlFor="pj-key" required error={!keyOk && form.key ? '영문 대문자·숫자 2~10자' : undefined}>
            <Input id="pj-key" value={form.key} disabled={editing} maxLength={10} onChange={(e) => set('key', e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))} placeholder="GW" />
          </Field>
          <Field label="이름" htmlFor="pj-title" required>
            <Input id="pj-title" value={form.title} maxLength={100} onChange={(e) => set('title', e.target.value)} placeholder="사내 그룹웨어 개발" />
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="시작일" htmlFor="pj-start" required><Input id="pj-start" type="date" value={form.startDate} onChange={(e) => set('startDate', e.target.value)} /></Field>
          <Field label="종료일" htmlFor="pj-end" required error={form.endDate && form.endDate < form.startDate ? '종료일은 시작일 뒤여야 합니다.' : undefined}>
            <Input id="pj-end" type="date" value={form.endDate} min={form.startDate} onChange={(e) => set('endDate', e.target.value)} />
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="담당 팀장" htmlFor="pj-leader" required hint="담당 팀장은 자동으로 멤버가 됩니다.">
            <Select id="pj-leader" value={form.leaderId} onChange={(e) => set('leaderId', e.target.value)}>
              <option value="">선택</option>
              {people.map((u) => <option key={u.id} value={u.id}>{u.name} {u.title} · {u.department}</option>)}
            </Select>
          </Field>
          <Field label="우선순위" htmlFor="pj-priority">
            <Select id="pj-priority" value={form.priority} onChange={(e) => set('priority', e.target.value)}>
              {PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY_META[p].label}</option>)}
            </Select>
          </Field>
        </div>
        <Field label="설명" htmlFor="pj-content"><Textarea id="pj-content" rows={3} value={form.content} maxLength={1000} onChange={(e) => set('content', e.target.value)} /></Field>
        {error && <p role="alert" className="text-[13px] text-red-600">{error}</p>}
      </div>
    </Modal>
  );
}

/** 멤버 바꾸기 (명세 3장: 담당 팀장은 항상 포함, 미완료 업무 담당자는 뺄 수 없음) */
export function MembersModal({ open, project, members, onClose, onSaved }) {
  const [people, setPeople] = useState([]);
  const [selected, setSelected] = useState(new Set());
  const [q, setQ] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setError('');
    setQ('');
    setSelected(new Set(members.map((m) => m.id)));
    userLookupApi.search({ size: 100 }).then(setPeople).catch(() => setPeople([]));
  }, [open, members]);

  // 퇴사 등으로 검색에 안 나오는 기존 멤버도 목록에 남김
  const list = useMemo(() => {
    const byId = new Map(people.map((p) => [p.id, p]));
    members.forEach((m) => { if (!byId.has(m.id)) byId.set(m.id, { ...m, department: '' }); });
    return [...byId.values()]
      .filter((p) => !q.trim() || p.name.includes(q.trim()))
      .sort((a, b) => Number(selected.has(b.id)) - Number(selected.has(a.id)) || a.name.localeCompare(b.name, 'ko'));
  }, [people, members, q, selected]);

  if (!open || !project) return null;
  const leaderId = project.leader?.id;
  const toggle = (id) => setSelected((s) => {
    const next = new Set(s);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  const save = async () => {
    setSaving(true);
    setError('');
    try {
      const saved = await projectApi.replaceMembers(project.id, [...selected]);
      toast.success(`멤버를 ${saved.memberCount}명으로 저장했습니다.`);
      onSaved(saved);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="프로젝트 멤버"
      description="멤버만 업무 담당자가 될 수 있습니다. 미완료 업무를 맡은 사람은 담당자를 바꾼 뒤에 뺄 수 있습니다."
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button loading={saving} onClick={save}>저장 ({selected.size}명)</Button></>}
    >
      <label className="block">
        <span className="sr-only">이름 검색</span>
        <Input icon={Search} value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름 검색" />
      </label>
      <ul className="mt-3 max-h-80 divide-y divide-gray-100 overflow-y-auto rounded-lg border border-gray-200">
        {list.map((p) => {
          const isLeader = p.id === leaderId;
          const on = isLeader || selected.has(p.id);
          return (
            <li key={p.id}>
              <button type="button" disabled={isLeader} onClick={() => toggle(p.id)} className={cn('flex w-full items-center gap-3 px-3 py-2 text-left', !isLeader && 'hover:bg-gray-50')}>
                <PersonAvatar person={p} size="sm" />
                <span className="min-w-0 flex-1 text-[13px]">
                  <span className="font-medium text-gray-900">{p.name}</span> <span className="text-gray-500">{p.title}{p.department && ` · ${p.department}`}</span>
                </span>
                {isLeader ? <Badge tone="blue">담당 팀장</Badge> : (
                  <span className={cn('flex h-5 w-5 items-center justify-center rounded border', on ? 'border-blue-600 bg-blue-600 text-white' : 'border-gray-300')}>
                    {on && <Check size={13} />}
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>
      {error && <p role="alert" className="mt-3 text-[13px] text-red-600">{error}</p>}
    </Modal>
  );
}
