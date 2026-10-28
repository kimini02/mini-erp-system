import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangle, ArrowDown, ArrowUp, FileText, Plus, RotateCcw, Save, Send, Trash2, X } from 'lucide-react';
import { approvalDocumentApi, approvalFormApi } from '@/api/approval';
import { leaveApi } from '@/api/leave';
import { userLookupApi } from '@/api/users';
import { attendanceApi } from '@/api/attendance';
import { CELL_META, dayCode, shiftDate, timeOf, todayLocal } from '@/lib/attendance';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { LEAVE_TYPE_OPTIONS, formatDays, hoursBetween, isHalfDay, leaveTitle, overtimeTitle } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { confirm } from '@/lib/confirm';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Avatar,
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Field,
  Input,
  PageHeader,
  PageLoader,
  Select,
  Textarea,
} from '@/components/ui';
import { PersonText } from '@/components/approval/ApprovalBits';

const EMPTY_BODY = {
  LEAVE: { leaveType: 'ANNUAL', startDate: '', endDate: '', handoverUserId: '', reason: '' },
  OVERTIME: { overtimeDate: '', startTime: '18:00', endTime: '21:00', reason: '' },
  GENERAL: { content: '', amount: '' },
  ATTENDANCE_FIX: { workDate: '', clockIn: '', clockOut: '', reason: '' },
};

const initialBody = (formCode, searchParams) => {
  if (!EMPTY_BODY[formCode]) return null;
  if (formCode === 'ATTENDANCE_FIX') return { ...EMPTY_BODY.ATTENDANCE_FIX, workDate: searchParams.get('date') || '' };
  return { ...EMPTY_BODY[formCode] };
};

const nowTime = () => new Date().toTimeString().slice(0, 5);
const WEEKEND = new Set([0, 6]);

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
function fixProblem(body, record) {
  if (record && !record.clockInAt && !body.clockIn)
    return { field: 'in', message: '이날은 출근 기록이 없어 출근 시각을 꼭 적어야 합니다.' };
  const at = (hhmm, existing) => (hhmm ? `${body.workDate}T${hhmm}` : existing ? existing.slice(0, 16) : null);
  const inAt = at(body.clockIn, record?.clockInAt);
  const outAt = at(body.clockOut, record?.clockOutAt);
  if (inAt && outAt && outAt <= inAt) {
    return {
      field: 'out',
      message: body.clockOut
        ? '퇴근 시각은 출근 시각보다 늦어야 합니다.'
        : `지금 퇴근 기록(${timeOf(record.clockOutAt)})보다 늦은 출근 시각입니다. 퇴근 시각도 함께 고쳐 주세요.`,
    };
  }
  return null;
}

/** 근태 정정: 정정할 날의 지금 기록을 보여 주고, 바꿀 시각만 입력 (M3 명세 5장) */
function AttendanceFixFields({ body, set, onRecord }) {
  const [current, setCurrent] = useState(null);
  const today = todayLocal();
  const min = shiftDate(today, -30);
  const max = shiftDate(today, -1);
  const validDate = body.workDate && body.workDate >= min && body.workDate <= max;

  useEffect(() => {
    if (!validDate) {
      setCurrent(null);
      return undefined;
    }
    let alive = true;
    attendanceApi
      .me(body.workDate.slice(0, 7))
      .then((r) => alive && setCurrent(r.days.find((d) => d.date === body.workDate) || null))
      .catch(() => alive && setCurrent(null));
    return () => {
      alive = false;
    };
  }, [body.workDate, validDate]);

  useEffect(() => {
    onRecord?.(current);
  }, [current, onRecord]);

  const meta = current ? CELL_META[dayCode(current)] || CELL_META.NONE : null;
  const needIn = Boolean(current && !current.clockInAt);
  const problem = fixProblem(body, current);
  return (
    <div className="space-y-4">
      <Field
        label="정정할 날짜"
        htmlFor="fix-date"
        required
        hint="최근 30일 안의 지난 날짜만 고칠 수 있습니다."
        error={body.workDate && !validDate ? '최근 30일 안의 지난 날짜를 골라 주세요.' : undefined}
      >
        <Input
          id="fix-date"
          type="date"
          min={min}
          max={max}
          value={body.workDate}
          onChange={(e) => set('workDate', e.target.value)}
        />
      </Field>
      {current && (
        <div className="rounded-lg bg-gray-50 px-3.5 py-3 text-[13px] text-gray-700">
          지금 기록: 출근 <b className="font-semibold text-gray-900">{timeOf(current.clockInAt) || '없음'}</b>
          {' · '}퇴근 <b className="font-semibold text-gray-900">{timeOf(current.clockOutAt) || '없음'}</b>
          {meta?.label && (
            <>
              {' '}
              · <Badge tone={meta.tone}>{meta.label}</Badge>
            </>
          )}
          {current.fixDocument && (
            <span className="ml-1 text-xs text-gray-500">(이미 {current.fixDocument.docNo}로 정정됨)</span>
          )}
        </div>
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field
          label="바른 출근 시각"
          htmlFor="fix-in"
          required={needIn}
          hint={needIn ? '이날은 출근 기록이 없어 출근 시각을 꼭 적어야 합니다.' : '바꾸지 않을 시각은 비워 두세요.'}
          error={problem?.field === 'in' && body.clockOut ? problem.message : undefined}
        >
          <Input id="fix-in" type="time" value={body.clockIn} onChange={(e) => set('clockIn', e.target.value)} />
        </Field>
        <Field label="바른 퇴근 시각" htmlFor="fix-out" error={problem?.field === 'out' ? problem.message : undefined}>
          <Input
            id="fix-out"
            type="time"
            value={body.clockOut}
            onChange={(e) => set('clockOut', e.target.value)}
            invalid={problem?.field === 'out'}
          />
        </Field>
      </div>
      <Field label="사유" htmlFor="fix-reason" required hint="예: 출입카드 오류로 출근을 늦게 찍었습니다.">
        <Textarea id="fix-reason" rows={3} value={body.reason} onChange={(e) => set('reason', e.target.value)} />
      </Field>
    </div>
  );
}

/* ───────── 양식별 입력 칸 ───────── */

function LeaveFields({ body, set, handoverOptions, days, daysError }) {
  const half = isHalfDay(body.leaveType);
  return (
    <div className="space-y-4">
      <Field label="휴가 종류" required>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          {LEAVE_TYPE_OPTIONS.map((t) => {
            const active = t.value === body.leaveType;
            return (
              <button
                key={t.value}
                type="button"
                onClick={() => set('leaveType', t.value)}
                className={cn(
                  'rounded-lg border px-3 py-2 text-left transition-colors',
                  active ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600' : 'border-gray-200 hover:border-gray-300',
                )}
              >
                <span className={cn('block text-[13px] font-medium', active ? 'text-blue-700' : 'text-gray-900')}>
                  {t.label}
                </span>
                <span className="block text-xs text-gray-500">{t.hint}</span>
              </button>
            );
          })}
        </div>
      </Field>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label={half ? '날짜' : '시작일'} htmlFor="lv-start" required>
          <Input id="lv-start" type="date" value={body.startDate} onChange={(e) => set('startDate', e.target.value)} />
        </Field>
        {!half && (
          <Field
            label="종료일"
            htmlFor="lv-end"
            required
            error={
              body.endDate && body.startDate && body.endDate < body.startDate
                ? '종료일이 시작일보다 빠릅니다.'
                : undefined
            }
          >
            <Input
              id="lv-end"
              type="date"
              min={body.startDate || undefined}
              value={body.endDate}
              onChange={(e) => set('endDate', e.target.value)}
            />
          </Field>
        )}
      </div>

      {(days || daysError) && (
        <div
          className={cn(
            'rounded-lg px-3.5 py-3 text-[13px]',
            daysError ? 'bg-red-50 text-red-700' : 'bg-gray-50 text-gray-700',
          )}
        >
          {daysError ||
            (body.leaveType === 'SICK' || body.leaveType === 'FAMILY' ? (
              '병가·경조 휴가는 연차에서 빼지 않습니다.'
            ) : (
              <>
                연차 <b className="font-semibold text-gray-900">{formatDays(days.days)}</b> 차감
                {days.offDays > 0 && ` · 주말·공휴일 ${days.offDays}일 제외`}
              </>
            ))}
        </div>
      )}

      <Field label="업무 인수자" htmlFor="lv-handover">
        <Select id="lv-handover" value={body.handoverUserId} onChange={(e) => set('handoverUserId', e.target.value)}>
          <option value="">지정 안 함</option>
          {handoverOptions.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} {p.title} · {p.department}
            </option>
          ))}
        </Select>
      </Field>

      <Field label="사유" htmlFor="lv-reason" hint="같은 날짜에 이미 신청한 휴가가 있으면 상신할 수 없습니다.">
        <Textarea id="lv-reason" rows={3} value={body.reason} onChange={(e) => set('reason', e.target.value)} />
      </Field>
    </div>
  );
}

function OvertimeFields({ body, set }) {
  const day = body.overtimeDate ? new Date(`${body.overtimeDate}T00:00:00`).getDay() : null;
  const hours = hoursBetween(body.startTime, body.endTime);
  const invalidTime = body.startTime && body.endTime && body.endTime <= body.startTime;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Field label="근무일" htmlFor="ot-date" required>
          <Input
            id="ot-date"
            type="date"
            value={body.overtimeDate}
            onChange={(e) => set('overtimeDate', e.target.value)}
          />
        </Field>
        <Field label="시작" htmlFor="ot-start" required>
          <Input id="ot-start" type="time" value={body.startTime} onChange={(e) => set('startTime', e.target.value)} />
        </Field>
        <Field
          label="종료"
          htmlFor="ot-end"
          required
          error={invalidTime ? '종료 시각은 시작 시각보다 늦어야 합니다.' : undefined}
        >
          <Input
            id="ot-end"
            type="time"
            value={body.endTime}
            onChange={(e) => set('endTime', e.target.value)}
            invalid={invalidTime}
          />
        </Field>
      </div>
      {hours && !invalidTime && (
        <p className="rounded-lg bg-gray-50 px-3.5 py-3 text-[13px] text-gray-700">
          <b className="font-semibold text-gray-900">{hours}</b>
          {day !== null && ` · ${WEEKEND.has(day) ? '주말 근무' : '평일 연장 근무'}`}
        </p>
      )}
      <Field label="업무 내용" htmlFor="ot-reason" required>
        <Textarea id="ot-reason" rows={3} value={body.reason} onChange={(e) => set('reason', e.target.value)} />
      </Field>
    </div>
  );
}

function GeneralFields({ title, setTitle, body, set }) {
  return (
    <div className="space-y-4">
      <Field label="제목" htmlFor="gn-title" required>
        <Input id="gn-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
      </Field>
      <Field label="금액" htmlFor="gn-amount" hint="비용이 없는 품의는 비워 두세요.">
        <Input
          id="gn-amount"
          type="number"
          min="0"
          step="1000"
          value={body.amount}
          onChange={(e) => set('amount', e.target.value)}
          trailing="원"
        />
      </Field>
      <Field label="내용" htmlFor="gn-content" required>
        <Textarea id="gn-content" rows={6} value={body.content} onChange={(e) => set('content', e.target.value)} />
      </Field>
    </div>
  );
}

/* ───────── 결재선 ───────── */

const ROW_STATUS = {
  SKIPPED: { label: '건너뜀', tone: 'gray' },
  FALLBACK: { label: '위로 넘김', tone: 'amber' },
  ERROR: { label: '결재자 없음', tone: 'red' },
};

function LinePanel({ me, form, preview, previewError, customLine, people, onStartEdit, onChangeLine, onReset }) {
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
          <LinePanel
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
