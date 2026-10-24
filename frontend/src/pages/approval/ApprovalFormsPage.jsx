import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, FileCog, Plus, Save, Trash2, Undo2 } from 'lucide-react';
import { approvalFormApi, userLookupApi } from '@/api/m2';
import { roleApi } from '@/api/m1';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import { APPROVER_TYPE_OPTIONS, FORM_OPTION_META } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Badge, Button, Card, CardHeader, EmptyState, PageHeader, PageLoader, Select } from '@/components/ui';
import { PersonText } from '@/components/approval/ApprovalBits';

const toEditable = (detail) => ({
  steps: detail.steps.map(({ lineKind, approverType, roleCode, userId }) => ({ lineKind, approverType, roleCode: roleCode || '', userId: userId ? String(userId) : '' })),
  options: { ...detail.options },
});
const stepsPayload = (steps) => steps.map((s) => ({
  lineKind: s.lineKind,
  approverType: s.approverType,
  roleCode: s.approverType === 'ROLE' ? s.roleCode : null,
  userId: s.approverType === 'USER' ? Number(s.userId) : null,
}));

const ROW_TONE = { OK: 'green', FALLBACK: 'amber', SKIPPED: 'gray', ERROR: 'red' };
const ROW_LABEL = { OK: '결재', FALLBACK: '위로 넘김', SKIPPED: '건너뜀', ERROR: '결재자 없음' };

function Toggle({ checked, onChange, disabled, label }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn('relative h-5 w-9 shrink-0 rounded-full transition-colors disabled:opacity-50', checked ? 'bg-blue-600' : 'bg-gray-300')}
    >
      <span className={cn('absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-all', checked ? 'left-[18px]' : 'left-0.5')} />
    </button>
  );
}

export default function ApprovalFormsPage() {
  const can = useAuthStore((s) => s.can);
  const canUpdate = can(MENU.APPROVAL_FORM, 'U');
  const [forms, setForms] = useState(null);
  const [code, setCode] = useState('');
  const [detail, setDetail] = useState(null);
  const [edit, setEdit] = useState(null);
  const [roles, setRoles] = useState([]);
  const [people, setPeople] = useState([]);
  const [drafterId, setDrafterId] = useState('');
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    approvalFormApi.list()
      .then((list) => {
        setForms(list);
        setCode((c) => c || list[0]?.code || '');
      })
      .catch((err) => setError(getErrorMessage(err)));
    if (can(MENU.ROLE)) roleApi.list().then(setRoles).catch(() => {});
    userLookupApi.search({ size: 100 }).then((list) => {
      setPeople(list);
      setDrafterId((d) => d || String(list[0]?.id || ''));
    }).catch(() => {});
  }, [can]);

  const loadDetail = useCallback(async () => {
    if (!code) return;
    try {
      const d = await approvalFormApi.get(code);
      setDetail(d);
      setEdit(toEditable(d));
      setSaveError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, [code]);

  useEffect(() => {
    setDetail(null);
    loadDetail();
  }, [loadDetail]);

  const loadPreview = useCallback(() => {
    if (!code || !drafterId) return;
    approvalFormApi.preview(code, Number(drafterId)).then(setPreview).catch((err) => setPreview({ rows: [], finalLine: [], error: getErrorMessage(err) }));
  }, [code, drafterId]);

  useEffect(() => {
    loadPreview();
  }, [loadPreview]);

  const dirty = useMemo(() => detail && edit && JSON.stringify(toEditable(detail)) !== JSON.stringify(edit), [detail, edit]);
  const approverCount = edit?.steps.filter((s) => s.lineKind === 'APPROVER').length ?? 0;
  const incomplete = edit?.steps.some((s) => (s.approverType === 'ROLE' && !s.roleCode) || (s.approverType === 'USER' && !s.userId));

  const setStep = (index, patch) => setEdit((e) => ({ ...e, steps: e.steps.map((s, i) => (i === index ? { ...s, ...patch } : s)) }));
  const moveStep = (index, delta) => setEdit((e) => {
    const steps = [...e.steps];
    const [item] = steps.splice(index, 1);
    steps.splice(index + delta, 0, item);
    return { ...e, steps };
  });
  const removeStep = (index) => setEdit((e) => ({ ...e, steps: e.steps.filter((_, i) => i !== index) }));
  const addStep = () => setEdit((e) => ({ ...e, steps: [...e.steps, { lineKind: 'APPROVER', approverType: 'DEPT_HEAD', roleCode: '', userId: '' }] }));

  const save = async () => {
    setSaving(true);
    setSaveError('');
    try {
      await approvalFormApi.saveSteps(code, stepsPayload(edit.steps));
      const updated = await approvalFormApi.updateOptions(code, edit.options);
      setDetail(updated);
      setEdit(toEditable(updated));
      setForms((list) => list?.map((f) => (f.code === code ? {
        ...f,
        allowLineEdit: updated.options.allowLineEdit,
        approverStepCount: updated.steps.filter((s) => s.lineKind === 'APPROVER').length,
        referenceStepCount: updated.steps.filter((s) => s.lineKind === 'REFERENCE').length,
      } : f)));
      toast.success('저장했습니다. 이미 상신된 문서의 결재선은 바뀌지 않고, 지금부터 기안하는 문서에 적용됩니다.');
      loadPreview();
    } catch (err) {
      setSaveError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  if (error) return <EmptyState icon={FileCog} title="결재 양식을 불러오지 못했습니다" description={error} />;
  if (!forms) return <PageLoader />;

  const form = forms.find((f) => f.code === code);

  return (
    <>
      <PageHeader
        title="결재 양식"
        description="양식마다 기본 결재선을 정해 두면, 기안할 때 기안자 부서에 맞게 결재자가 채워집니다."
        actions={canUpdate && (
          <div className="flex gap-2">
            <Button variant="secondary" icon={Undo2} disabled={!dirty || saving} onClick={() => setEdit(toEditable(detail))}>변경 취소</Button>
            <Button icon={Save} loading={saving} disabled={!dirty || approverCount === 0 || incomplete} onClick={save}>저장</Button>
          </div>
        )}
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <Card className="h-fit lg:col-span-3">
          <CardHeader title={`양식 ${forms.length}`} />
          <ul className="p-2">
            {forms.map((f) => (
              <li key={f.code}>
                <button
                  type="button"
                  onClick={() => setCode(f.code)}
                  className={cn('w-full rounded-lg px-3 py-2.5 text-left transition-colors', f.code === code ? 'bg-blue-50' : 'hover:bg-gray-50')}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className={cn('text-sm font-medium', f.code === code ? 'text-blue-700' : 'text-gray-900')}>{f.name}</span>
                    <span className="text-xs tabular-nums text-gray-400">{f.prefix}</span>
                  </span>
                  <span className="mt-0.5 block text-xs text-gray-500">
                    결재 {f.approverStepCount}단계{f.referenceStepCount ? ` · 참조 ${f.referenceStepCount}` : ''}{f.allowLineEdit ? ' · 결재선 수정 가능' : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <p className="border-t border-gray-100 px-4 py-3 text-xs text-gray-500">1차 버전은 양식 3종을 고정으로 씁니다. 양식 추가와 조건 분기는 다음 단계에서 지원합니다.</p>
        </Card>

        <div className="space-y-4 lg:col-span-9">
          {!detail || !edit ? <PageLoader /> : (
            <>
              <Card>
                <CardHeader
                  title={`${form?.name} 기본 결재선`}
                  description="위에서부터 차례로 결재합니다. 참조는 결재 순서와 상관없이 문서를 받아 봅니다."
                  action={canUpdate && <Button variant="secondary" size="sm" icon={Plus} onClick={addStep}>단계 추가</Button>}
                />
                <ol className="divide-y divide-gray-100">
                  {edit.steps.map((s, i) => (
                    <li key={i} className="flex flex-col gap-2 px-5 py-3 md:flex-row md:items-center">
                      <span className="flex w-32 shrink-0 items-center gap-2">
                        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-gray-100 text-xs font-semibold text-gray-600">{i + 1}</span>
                        <Select
                          aria-label={`${i + 1}단계 종류`}
                          value={s.lineKind}
                          disabled={!canUpdate}
                          onChange={(e) => setStep(i, { lineKind: e.target.value })}
                          className="w-24"
                        >
                          <option value="APPROVER">결재</option>
                          <option value="REFERENCE">참조</option>
                        </Select>
                      </span>
                      <Select aria-label={`${i + 1}단계 결재자 지정 방식`} value={s.approverType} disabled={!canUpdate} onChange={(e) => setStep(i, { approverType: e.target.value })} className="md:w-52">
                        {APPROVER_TYPE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                      </Select>
                      {s.approverType === 'ROLE' && (
                        <Select aria-label="역할" value={s.roleCode} disabled={!canUpdate} onChange={(e) => setStep(i, { roleCode: e.target.value })} invalid={!s.roleCode} className="md:w-48">
                          <option value="">역할 선택</option>
                          {(roles.length ? roles : [{ code: s.roleCode, name: s.roleCode }]).filter((r) => r.code).map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
                        </Select>
                      )}
                      {s.approverType === 'USER' && (
                        <Select aria-label="사원" value={s.userId} disabled={!canUpdate} onChange={(e) => setStep(i, { userId: e.target.value })} invalid={!s.userId} className="md:w-56">
                          <option value="">사원 선택</option>
                          {people.map((p) => <option key={p.id} value={p.id}>{p.name} {p.title} · {p.department}</option>)}
                        </Select>
                      )}
                      <span className="flex-1" />
                      {canUpdate && (
                        <span className="flex shrink-0 gap-0.5">
                          <Button variant="ghost" size="sm" icon={ArrowUp} disabled={i === 0} onClick={() => moveStep(i, -1)} aria-label="위로" />
                          <Button variant="ghost" size="sm" icon={ArrowDown} disabled={i === edit.steps.length - 1} onClick={() => moveStep(i, 1)} aria-label="아래로" />
                          <Button variant="ghost" size="sm" icon={Trash2} onClick={() => removeStep(i)} aria-label={`${i + 1}단계 삭제`} />
                        </span>
                      )}
                    </li>
                  ))}
                </ol>
                {approverCount === 0 && <p className="border-t border-gray-100 px-5 py-3 text-[13px] text-red-600">결재자 단계가 하나 이상 있어야 합니다.</p>}
                {saveError && <p role="alert" className="border-t border-gray-100 px-5 py-3 text-[13px] text-red-600">{saveError}</p>}
              </Card>

              <Card>
                <CardHeader title="예외 처리" description="결재선을 사람으로 풀 때 적용하는 규칙입니다." />
                <ul className="divide-y divide-gray-100">
                  {FORM_OPTION_META.map((o) => (
                    <li key={o.key} className="flex items-start justify-between gap-4 px-5 py-3">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{o.label}</p>
                        <p className="mt-0.5 text-[13px] text-gray-500">{o.desc}</p>
                      </div>
                      <Toggle
                        label={o.label}
                        checked={Boolean(edit.options[o.key])}
                        disabled={!canUpdate}
                        onChange={(v) => setEdit((e) => ({ ...e, options: { ...e.options, [o.key]: v } }))}
                      />
                    </li>
                  ))}
                </ul>
              </Card>

              <Card>
                <CardHeader
                  title="미리보기"
                  description={dirty ? '저장한 규칙 기준입니다. 바꾼 내용은 저장한 뒤에 반영됩니다.' : '이 사람이 기안하면 결재선이 이렇게 만들어집니다.'}
                  action={(
                    <Select aria-label="기안자" value={drafterId} onChange={(e) => setDrafterId(e.target.value)} className="w-60">
                      {people.map((p) => <option key={p.id} value={p.id}>{p.name} · {p.department} {p.title}</option>)}
                    </Select>
                  )}
                />
                {!preview ? <PageLoader /> : (
                  <>
                    <ul className="divide-y divide-gray-100">
                      {preview.rows.map((r) => (
                        <li key={`${r.order}-${r.rule}`} className="grid grid-cols-[48px_1fr] gap-3 px-5 py-3 sm:grid-cols-[48px_180px_1fr_auto]">
                          <span className="text-xs font-medium text-gray-400">{r.kind === 'REFERENCE' ? '참조' : `${r.order}단계`}</span>
                          <span className="text-[13px] text-gray-600">{r.rule}</span>
                          <span className="text-[13px]">
                            {r.result ? <PersonText person={r.result} /> : <span className="text-red-600">결재자 없음</span>}
                            {r.note && <span className="block text-xs text-gray-500">{r.note}</span>}
                          </span>
                          <Badge tone={ROW_TONE[r.status] || 'gray'}>{r.kind === 'REFERENCE' && r.status === 'OK' ? '참조' : ROW_LABEL[r.status] || r.status}</Badge>
                        </li>
                      ))}
                    </ul>
                    <div className="border-t border-gray-100 px-5 py-3 text-[13px]">
                      {preview.error ? (
                        <span className="text-red-600">{preview.error}</span>
                      ) : (
                        <>
                          <span className="text-gray-500">최종 결재선 </span>
                          <b className="font-semibold text-gray-900">{preview.finalLine.map((p) => `${p.name} ${p.title}`).join(' → ') || '결재자 없음 (상신하면 바로 승인)'}</b>
                        </>
                      )}
                    </div>
                  </>
                )}
              </Card>
            </>
          )}
        </div>
      </div>
    </>
  );
}
