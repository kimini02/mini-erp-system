import { useState } from 'react';
import { AlertTriangle, ArrowDown, ArrowUp, Plus, RotateCcw, X } from 'lucide-react';
import { cn } from '@/lib/cn';
import { Avatar, Badge, Button, Card, CardHeader, Select } from '@/components/ui';
import { PersonText } from '@/components/approval/ApprovalBits';

const ROW_STATUS = {
  SKIPPED: { label: '건너뜀', tone: 'gray' },
  FALLBACK: { label: '위로 넘김', tone: 'amber' },
  ERROR: { label: '결재자 없음', tone: 'red' },
};

export default function ApprovalLinePanel({
  me,
  form,
  preview,
  previewError,
  customLine,
  people,
  onStartEdit,
  onChangeLine,
  onReset,
}) {
  const [pick, setPick] = useState('');
  const [addError, setAddError] = useState('');
  const editing = customLine !== null;
  const approverRows = (preview?.rows || []).filter((r) => (r.kind || 'APPROVER') === 'APPROVER');
  const references = preview?.references || [];

  const approvers = editing ? customLine.filter((l) => l.lineKind === 'APPROVER') : [];
  const move = (index, delta) => {
    const list = [...approvers];
    const [item] = list.splice(index, 1);
    list.splice(index + delta, 0, item);
    onChangeLine([...list, ...customLine.filter((l) => l.lineKind !== 'APPROVER')]);
  };
  const remove = (userId, lineKind) =>
    onChangeLine(customLine.filter((l) => !(l.userId === userId && l.lineKind === lineKind)));
  const add = () => {
    const person = people.find((p) => String(p.id) === pick);
    if (!person) return;
    if (customLine.some((l) => l.userId === person.id && l.lineKind === 'APPROVER')) {
      setAddError('이미 결재선에 있는 사람입니다. 같은 사람은 한 번만 결재합니다.');
      return;
    }
    setAddError('');
    setPick('');
    onChangeLine([
      ...approvers,
      { userId: person.id, lineKind: 'APPROVER', person },
      ...customLine.filter((l) => l.lineKind !== 'APPROVER'),
    ]);
  };

  return (
    <Card>
      <CardHeader
        title="결재선"
        description={editing ? '직접 지정한 결재선' : form ? '양식 규칙으로 자동 생성' : '양식을 고르면 채워집니다'}
        action={
          form?.allowLineEdit &&
          preview &&
          (editing ? (
            <Button variant="ghost" size="sm" icon={RotateCcw} onClick={onReset}>
              규칙대로
            </Button>
          ) : (
            <Button variant="ghost" size="sm" onClick={onStartEdit}>
              결재선 바꾸기
            </Button>
          ))
        }
      />
      <div className="px-5 py-3">
        <ul className="divide-y divide-gray-100">
          <li className="flex items-center gap-3 py-2.5">
            <span className="w-8 shrink-0 text-xs font-medium text-gray-400">기안</span>
            <Avatar name={me?.name || '나'} size="sm" />
            <span className="min-w-0 text-[13px]">
              <span className="font-medium text-gray-900">{me?.name}</span> <span className="text-gray-500">(나)</span>
            </span>
          </li>

          {editing
            ? approvers.map((l, i) => (
                <li key={l.userId} className="flex items-center gap-3 py-2.5">
                  <span className="w-8 shrink-0 text-xs font-medium text-gray-400">{i + 1}</span>
                  <Avatar name={l.person?.name || '?'} size="sm" />
                  <span className="min-w-0 flex-1 text-[13px]">
                    <PersonText person={l.person} />
                  </span>
                  <span className="flex shrink-0 gap-0.5">
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={ArrowUp}
                      disabled={i === 0}
                      onClick={() => move(i, -1)}
                      aria-label="위로"
                    />
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={ArrowDown}
                      disabled={i === approvers.length - 1}
                      onClick={() => move(i, 1)}
                      aria-label="아래로"
                    />
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={X}
                      onClick={() => remove(l.userId, 'APPROVER')}
                      aria-label={`${l.person?.name} 빼기`}
                    />
                  </span>
                </li>
              ))
            : approverRows.map((r) => {
                const status = ROW_STATUS[r.status];
                return (
                  <li key={r.order} className={cn('flex gap-3 py-2.5', r.status === 'SKIPPED' && 'opacity-60')}>
                    <span className="mt-1 w-8 shrink-0 text-xs font-medium text-gray-400">{r.order}</span>
                    <Avatar name={r.result?.name || '?'} size="sm" />
                    <div className="min-w-0 flex-1 text-[13px]">
                      {r.result ? <PersonText person={r.result} /> : <span className="text-red-600">결재자 없음</span>}
                      <p className="text-xs text-gray-500">{r.rule}</p>
                      {r.note && <p className="mt-0.5 text-xs text-gray-500">{r.note}</p>}
                    </div>
                    {status && <Badge tone={status.tone}>{status.label}</Badge>}
                  </li>
                );
              })}
        </ul>

        {editing && (
          <div className="mt-2 space-y-1.5 border-t border-gray-100 pt-3">
            <div className="flex gap-2">
              <Select
                value={pick}
                onChange={(e) => {
                  setPick(e.target.value);
                  setAddError('');
                }}
                aria-label="결재자 추가"
              >
                <option value="">결재자 추가…</option>
                {people
                  .filter((p) => p.id !== me?.id && !approvers.some((a) => a.userId === p.id))
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} {p.title} · {p.department}
                    </option>
                  ))}
              </Select>
              <Button variant="secondary" icon={Plus} disabled={!pick} onClick={add}>
                추가
              </Button>
            </div>
            {addError && <p className="text-xs text-red-600">{addError}</p>}
            {approvers.length === 0 && (
              <p className="text-xs text-red-600">결재자가 한 명 이상 있어야 상신할 수 있습니다.</p>
            )}
          </div>
        )}

        {(previewError || preview?.error) && (
          <p className="mt-2 flex gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-[13px] text-red-700">
            <AlertTriangle size={15} className="mt-0.5 shrink-0" />
            {previewError || '부서장이 지정되지 않아 결재선을 만들 수 없습니다. 관리자에게 문의하세요.'}
          </p>
        )}
      </div>

      {(references.length > 0 || (editing && customLine.some((l) => l.lineKind === 'REFERENCE'))) && (
        <div className="border-t border-gray-100 px-5 py-3">
          <p className="mb-1.5 text-xs font-medium text-gray-400">참조</p>
          <ul className="space-y-1 text-[13px]">
            {(editing
              ? customLine.filter((l) => l.lineKind === 'REFERENCE').map((l) => ({ ...l.person, id: l.userId }))
              : references
            ).map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-2">
                <PersonText person={r} />
                {editing && (
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={X}
                    onClick={() => remove(r.id, 'REFERENCE')}
                    aria-label={`${r.name} 참조 빼기`}
                  />
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

/* ───────── 화면 ───────── */
