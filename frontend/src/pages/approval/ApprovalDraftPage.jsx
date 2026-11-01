import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { FileText, Save, Send, Trash2 } from 'lucide-react';
import { approvalDocumentApi, approvalFormApi } from '@/api/approval';
import { leaveApi } from '@/api/leave';
import { userLookupApi } from '@/api/users';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { formatDays, isHalfDay, leaveTitle, overtimeTitle } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { confirm } from '@/lib/confirm';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Button, Card, CardHeader, EmptyState, PageHeader, PageLoader } from '@/components/ui';
import OvertimeFields from './draft/OvertimeFields';
import { fixProblem } from './draft/helpers';
import AttendanceFixFields from './draft/AttendanceFixFields';
import LeaveFields from './draft/LeaveFields';
import GeneralFields from './draft/GeneralFields';
import ApprovalLinePanel from './draft/ApprovalLinePanel';

const EMPTY_BODY = {
  LEAVE: { leaveType: 'ANNUAL', startDate: '', endDate: '', handoverUserId: '', reason: '' },
  OVERTIME: { overtimeDate: '', startTime: '18:00', endTime: '21:00', reason: '' },
  GENERAL: { content: '', amount: '' },
  ATTENDANCE_FIX: { workDate: '', clockIn: '', clockOut: '', reason: '' },
};

const initialBody = (formCode, searchParams) => {
  if (!EMPTY_BODY[formCode]) return null;
  // 캘린더·내 근태에서 날짜를 골라 들어오면 그 날짜로 채웁니다(?date=YYYY-MM-DD).
  const date = /^\d{4}-\d{2}-\d{2}$/.test(searchParams.get('date') || '') ? searchParams.get('date') : '';
  if (formCode === 'ATTENDANCE_FIX') return { ...EMPTY_BODY.ATTENDANCE_FIX, workDate: date };
  if (formCode === 'LEAVE') return { ...EMPTY_BODY.LEAVE, startDate: date, endDate: date };
  if (formCode === 'OVERTIME') return { ...EMPTY_BODY.OVERTIME, overtimeDate: date };
  return { ...EMPTY_BODY[formCode] };
};

const nowTime = () => new Date().toTimeString().slice(0, 5);
/** 서버로 보낼 본문 (양식마다 다름, 명세 3.1) */
function toRequestBody(formCode, body) {
  if (formCode === 'LEAVE') {
    return {
      leaveType: body.leaveType,
      startDate: body.startDate,
      endDate: isHalfDay(body.leaveType) ? body.startDate : body.endDate,
      handoverUserId: body.handoverUserId ? Number(body.handoverUserId) : null,
      reason: body.reason.trim() || null,
    };
  }
  if (formCode === 'OVERTIME') {
    return {
      overtimeDate: body.overtimeDate,
      startTime: body.startTime,
      endTime: body.endTime,
      reason: body.reason.trim(),
    };
  }
  if (formCode === 'ATTENDANCE_FIX') {
    return {
      workDate: body.workDate,
      clockIn: body.clockIn || null,
      clockOut: body.clockOut || null,
      reason: body.reason.trim(),
    };
  }
  return { content: body.content.trim(), amount: body.amount === '' ? null : Number(body.amount) };
}

/** 문서 상세(임시 저장) → 입력 칸 값 */
function fromDocument(doc) {
  const b = doc.body || {};
  if (doc.form.code === 'LEAVE') {
    return {
      leaveType: b.leaveType,
      startDate: b.startDate || '',
      endDate: b.endDate || '',
      handoverUserId: b.handover?.id ? String(b.handover.id) : '',
      reason: b.reason || '',
    };
  }
  if (doc.form.code === 'OVERTIME') {
    return {
      overtimeDate: b.overtimeDate || '',
      startTime: (b.startTime || '').slice(0, 5),
      endTime: (b.endTime || '').slice(0, 5),
      reason: b.reason || '',
    };
  }
  if (doc.form.code === 'ATTENDANCE_FIX') {
    return {
      workDate: b.workDate || '',
      clockIn: (b.clockIn || '').slice(0, 5),
      clockOut: (b.clockOut || '').slice(0, 5),
      reason: b.reason || '',
    };
  }
  return { content: b.content || '', amount: b.amount ?? '' };
}

function isComplete(formCode, title, body, fixRecord = null) {
  if (formCode === 'LEAVE') {
    if (!body.startDate) return false;
    return isHalfDay(body.leaveType) || (body.endDate && body.endDate >= body.startDate);
  }
  if (formCode === 'OVERTIME')
    return Boolean(
      body.overtimeDate && body.startTime && body.endTime && body.endTime > body.startTime && body.reason.trim(),
    );
  if (formCode === 'GENERAL') return Boolean(title.trim() && body.content.trim());
  if (formCode === 'ATTENDANCE_FIX') {
    return Boolean(
      body.workDate && (body.clockIn || body.clockOut) && body.reason.trim() && !fixProblem(body, fixRecord),
    );
  }
  return false;
}

/**
 * 근태 정정 입력을 지금 기록과 합쳐 본 문제 (M3 명세 5장)
 * - 출근 기록이 없는 날(결근·기록 없음)은 출근 시각이 꼭 있어야 함
 * - 합친 결과의 퇴근이 출근보다 늦어야 함 (비워 둔 칸은 지금 기록을 그대로 씀)
 */
export default function ApprovalDraftPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const me = useAuthStore((s) => s.user);
  const refreshCounts = useApprovalStore((s) => s.refresh);
  const draftParam = searchParams.get('draft');

  const [forms, setForms] = useState(null);
  const [people, setPeople] = useState([]);
  const [formCode, setFormCode] = useState(searchParams.get('form') || '');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState(() => initialBody(searchParams.get('form'), searchParams));
  const [customLine, setCustomLine] = useState(null);
  const [draftId, setDraftId] = useState(draftParam ? Number(draftParam) : null);
  const [fixRecord, setFixRecord] = useState(null); // 근태 정정: 고칠 날의 지금 기록
  const [loadError, setLoadError] = useState('');
  const [loadingDraft, setLoadingDraft] = useState(Boolean(draftParam));

  const [preview, setPreview] = useState(null);
  const [previewError, setPreviewError] = useState('');
  const [days, setDays] = useState(null);
  const [daysError, setDaysError] = useState('');
  const [balance, setBalance] = useState(null);
  const [busy, setBusy] = useState('');
  const [formError, setFormError] = useState('');

  useEffect(() => {
    approvalFormApi
      .list()
      .then(setForms)
      .catch((err) => setLoadError(getErrorMessage(err)));
    userLookupApi
      .search({ size: 100 })
      .then(setPeople)
      .catch(() => {});
    leaveApi
      .balance(new Date().getFullYear())
      .then(setBalance)
      .catch(() => {});
  }, []);

  // 임시 저장한 문서 이어서 쓰기
  useEffect(() => {
    if (!draftParam) return;
    let alive = true;
    approvalDocumentApi
      .get(draftParam)
      .then((doc) => {
        if (!alive) return;
        if (doc.status !== 'DRAFT' || doc.drafter?.id !== me?.id) {
          setLoadError('임시 저장 중인 내 문서만 이어서 쓸 수 있습니다.');
          return;
        }
        setFormCode(doc.form.code);
        setTitle(doc.title);
        setBody(fromDocument(doc));
        setDraftId(doc.id);
        // 직접 지정한 결재선이 있으면 그대로 (임시 저장 문서의 line은 기안자가 고른 사람만 들어 있음)
        const saved =
          doc.customLine ||
          (doc.line?.length
            ? [
                ...doc.line.map((l) => ({ userId: l.approver.id, lineKind: 'APPROVER', person: l.approver })),
                ...(doc.references || []).map((r) => ({ userId: r.id, lineKind: 'REFERENCE', person: r })),
              ]
            : null);
        setCustomLine(saved ? saved.map((l) => ({ ...l, person: l.person || null })) : null);
      })
      .catch((err) => alive && setLoadError(getErrorMessage(err)))
      .finally(() => alive && setLoadingDraft(false));
    return () => {
      alive = false;
    };
  }, [draftParam, me?.id]);

  const peopleById = useMemo(() => new Map(people.map((p) => [p.id, p])), [people]);
  // 저장된 결재선에 이름 붙이기
  const decoratedLine = useMemo(
    () =>
      customLine
        ? customLine.map((l) => ({
            ...l,
            person: l.person || peopleById.get(l.userId) || { id: l.userId, name: `#${l.userId}` },
          }))
        : null,
    [customLine, peopleById],
  );

  const form = forms?.find((f) => f.code === formCode) || null;
  const lineKey = JSON.stringify(customLine?.map(({ userId, lineKind }) => ({ userId, lineKind })) ?? null);

  // 결재선 미리보기
  useEffect(() => {
    if (!formCode) return undefined;
    let alive = true;
    const line = JSON.parse(lineKey);
    if (line && !line.some((l) => l.lineKind === 'APPROVER')) {
      setPreview({ rows: [], finalLine: [], references: [], error: null });
      return undefined;
    }
    approvalDocumentApi
      .previewLine(formCode, line)
      .then((p) => {
        if (alive) {
          setPreview(p);
          setPreviewError('');
        }
      })
      .catch((err) => {
        if (alive) {
          setPreview(null);
          setPreviewError(getErrorMessage(err));
        }
      });
    return () => {
      alive = false;
    };
  }, [formCode, lineKey]);

  // 휴가 일수 미리 계산
  const leaveKey =
    formCode === 'LEAVE' && body
      ? `${body.leaveType}|${body.startDate}|${isHalfDay(body.leaveType) ? body.startDate : body.endDate}`
      : '';
  useEffect(() => {
    const [leaveType, startDate, endDate] = leaveKey.split('|');
    if (!leaveKey || !startDate || !endDate || endDate < startDate) {
      setDays(null);
      setDaysError('');
      return undefined;
    }
    let alive = true;
    const id = setTimeout(() => {
      leaveApi
        .previewDays({ leaveType, startDate, endDate })
        .then((r) => {
          if (alive) {
            setDays(r);
            setDaysError(r.error || '');
          }
        })
        .catch((err) => {
          if (alive) {
            setDays(null);
            setDaysError(getErrorMessage(err));
          }
        });
    }, 250);
    return () => {
      alive = false;
      clearTimeout(id);
    };
  }, [leaveKey]);

  const chooseForm = (code) => {
    if (code === formCode || draftId) return;
    setFormCode(code);
    setBody(initialBody(code, searchParams));
    setTitle('');
    setCustomLine(null);
    setPreview(null);
    setFormError('');
  };

  const set = useCallback((key, value) => setBody((b) => ({ ...b, [key]: value })), []);

  const autoTitle =
    formCode === 'LEAVE'
      ? leaveTitle(body?.leaveType, body?.startDate, isHalfDay(body?.leaveType) ? body?.startDate : body?.endDate)
      : formCode === 'OVERTIME'
        ? overtimeTitle(body?.overtimeDate)
        : formCode === 'ATTENDANCE_FIX'
          ? `근태 정정${body?.workDate ? ` · ${Number(body.workDate.slice(5, 7))}/${Number(body.workDate.slice(8, 10))}` : ''}`
          : title;
  const complete = Boolean(form && body && isComplete(formCode, title, body, fixRecord));
  const lineReady = Boolean(
    preview && !preview.error && !previewError && (preview.finalLine?.length > 0 || (preview.rows || []).length > 0),
  );
  const canSubmit = complete && lineReady && !(formCode === 'LEAVE' && daysError);

  const payload = () => ({
    formCode,
    title: autoTitle.trim(),
    body: toRequestBody(formCode, body),
    line: customLine ? customLine.map(({ userId, lineKind }) => ({ userId, lineKind })) : null,
  });

  /** 임시 저장 (없으면 만들고, 있으면 고침) → 문서 id */
  const save = async () => {
    if (draftId) {
      await approvalDocumentApi.updateDraft(draftId, payload());
      return draftId;
    }
    const created = await approvalDocumentApi.createDraft(payload());
    setDraftId(created.id);
    setSearchParams({ draft: String(created.id) }, { replace: true });
    return created.id;
  };

  const onSaveDraft = async () => {
    setBusy('draft');
    setFormError('');
    try {
      await save();
      toast.success(`임시 보관함에 저장했습니다 (${nowTime()}). 결재함 › 임시 보관에서 이어서 쓸 수 있습니다.`);
      refreshCounts();
    } catch (err) {
      setFormError(getErrorMessage(err));
    } finally {
      setBusy('');
    }
  };

  const onSubmit = async () => {
    setBusy('submit');
    setFormError('');
    try {
      const id = await save();
      const doc = await approvalDocumentApi.submit(id);
      const first = doc.line.find((l) => l.status === 'PENDING');
      toast.success(
        first
          ? `상신했습니다. ${doc.docNo} · ${first.approver.name} ${first.approver.title}에게 결재 요청 알림을 보냈습니다.`
          : `상신했습니다. ${doc.docNo} · 결재할 사람이 없어 바로 승인되었습니다.`,
      );
      refreshCounts();
      navigate(`/approvals/${id}`);
    } catch (err) {
      setFormError(getErrorMessage(err));
      refreshCounts();
    } finally {
      setBusy('');
    }
  };

  const onDelete = async () => {
    const ok = await confirm({
      title: '임시 저장한 문서를 지울까요?',
      description: '지운 문서는 되돌릴 수 없습니다.',
      confirmText: '삭제',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await approvalDocumentApi.removeDraft(draftId);
      toast.success('임시 저장한 문서를 지웠습니다.');
      refreshCounts();
      navigate('/approvals?box=drafts');
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  if (loadError) {
    return (
      <EmptyState
        icon={FileText}
        title="기안 화면을 열 수 없습니다"
        description={loadError}
        action={
          <Button variant="secondary" onClick={() => navigate('/approvals')}>
            결재함으로
          </Button>
        }
      />
    );
  }
  if (!forms || loadingDraft) return <PageLoader />;

  const handoverOptions = people.filter((p) => p.id !== me?.id && p.departmentId === me?.department?.id);
  const prefix = form?.prefix;

  return (
    <>
      <PageHeader
        title={draftId ? '기안 이어서 쓰기' : '기안하기'}
        description="양식을 고르고 내용을 쓰면 회사 규칙에 맞는 결재선이 자동으로 채워집니다."
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <div className="space-y-4 lg:col-span-8">
          <Card>
            <CardHeader
              title="양식"
              description={draftId ? '임시 저장한 문서는 양식을 바꿀 수 없습니다.' : undefined}
            />
            <div className="grid grid-cols-1 gap-2 p-4 sm:grid-cols-2 xl:grid-cols-4">
              {forms.map((f) => {
                const active = f.code === formCode;
                return (
                  <button
                    key={f.code}
                    type="button"
                    disabled={Boolean(draftId) && !active}
                    onClick={() => chooseForm(f.code)}
                    className={cn(
                      'rounded-lg border p-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50',
                      active
                        ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600'
                        : 'border-gray-200 hover:border-gray-300',
                    )}
                  >
                    <span className={cn('block text-sm font-semibold', active ? 'text-blue-700' : 'text-gray-900')}>
                      {f.name}
                    </span>
                    <span className="mt-0.5 block text-xs text-gray-500">{f.description}</span>
                  </button>
                );
              })}
            </div>
          </Card>

          {form && body && (
            <Card>
              <CardHeader
                title={form.name}
                description={`문서번호는 상신할 때 매겨집니다 (${prefix}-${new Date().getFullYear()}-____)`}
              />
              <div className="p-5">
                {formCode === 'LEAVE' && (
                  <LeaveFields
                    body={body}
                    set={set}
                    handoverOptions={handoverOptions}
                    days={days}
                    daysError={daysError}
                  />
                )}
                {formCode === 'OVERTIME' && <OvertimeFields body={body} set={set} />}
                {formCode === 'GENERAL' && <GeneralFields title={title} setTitle={setTitle} body={body} set={set} />}
                {formCode === 'ATTENDANCE_FIX' && <AttendanceFixFields body={body} set={set} onRecord={setFixRecord} />}
                {formCode !== 'GENERAL' && autoTitle && (
                  <p className="mt-4 text-xs text-gray-500">
                    제목: <span className="text-gray-700">{autoTitle}</span>
                  </p>
                )}
                {formError && (
                  <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3.5 py-3 text-[13px] text-red-700">
                    {formError}
                  </p>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2 border-t border-gray-100 px-5 py-3">
                {draftId && (
                  <Button variant="ghost" icon={Trash2} onClick={onDelete}>
                    임시 저장 삭제
                  </Button>
                )}
                <span className="flex-1" />
                <Button
                  variant="secondary"
                  icon={Save}
                  loading={busy === 'draft'}
                  disabled={Boolean(busy) || !complete}
                  onClick={onSaveDraft}
                >
                  임시 저장
                </Button>
                <Button
                  icon={Send}
                  loading={busy === 'submit'}
                  disabled={Boolean(busy) || !canSubmit}
                  onClick={onSubmit}
                >
                  상신
                </Button>
              </div>
            </Card>
          )}
        </div>

        <div className="space-y-4 lg:col-span-4">
          <ApprovalLinePanel
            me={me}
            form={form}
            preview={preview}
            previewError={previewError}
            customLine={decoratedLine}
            people={people}
            onStartEdit={() =>
              setCustomLine([
                ...(preview?.finalLine || []).map((p) => ({ userId: p.id, lineKind: 'APPROVER', person: p })),
                ...(preview?.references || []).map((p) => ({ userId: p.id, lineKind: 'REFERENCE', person: p })),
              ])
            }
            onChangeLine={setCustomLine}
            onReset={() => setCustomLine(null)}
          />

          {formCode === 'LEAVE' && (
            <Card>
              <CardHeader
                title="내 연차"
                action={
                  <Link to="/leaves" className="text-[13px] text-blue-700 hover:underline">
                    원장 보기
                  </Link>
                }
              />
              <dl className="divide-y divide-gray-100 px-5 py-1 text-sm">
                <div className="flex justify-between py-2.5">
                  <dt className="text-gray-500">사용 가능</dt>
                  <dd className="font-semibold tabular-nums text-gray-900">
                    {formatDays(days?.available ?? balance?.available)}
                  </dd>
                </div>
                <div className="flex justify-between py-2.5">
                  <dt className="text-gray-500">이번 신청</dt>
                  <dd className="tabular-nums text-gray-900">{days ? `−${formatDays(days.days)}` : '-'}</dd>
                </div>
                <div className="flex justify-between py-2.5">
                  <dt className="text-gray-500">신청 후</dt>
                  <dd className={cn('font-semibold tabular-nums', days?.after < 0 ? 'text-red-600' : 'text-gray-900')}>
                    {days ? formatDays(days.after) : '-'}
                  </dd>
                </div>
              </dl>
              <p className="border-t border-gray-100 px-5 py-3 text-xs text-gray-500">
                상신하면 결재가 끝날 때까지 이 일수를 예약해 두고, 반려되거나 회수하면 돌려받습니다.
              </p>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}
