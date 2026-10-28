import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Check, CornerUpLeft, FileX2, RotateCcw, X } from 'lucide-react';
import { approvalDocumentApi } from '@/api/m2';
import { useApprovalStore } from '@/store/approvalStore';
import {
  HISTORY_ACTION_LABELS,
  LEAVE_TYPE_NAMES,
  LINE_STATUS_META,
  SKIP_REASON_LABELS,
  formatAmount,
  formatDateTime,
  formatDay,
  formatDays,
  hoursBetween,
} from '@/lib/approval';
import { CELL_META } from '@/lib/attendance';
import { getErrorCode, getErrorMessage } from '@/lib/errors';
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
  Modal,
  PageLoader,
  Textarea,
} from '@/components/ui';
import { DocStatusBadge, PersonText } from '@/components/approval/ApprovalBits';

/** 마지막 승인 때 함께 일어나는 일 (양식별) */
const FINAL_APPROVE_EFFECT = {
  LEAVE: '승인하면 예약한 연차가 사용으로 바뀝니다.',
  ATTENDANCE_FIX: '승인하면 그날 근태 기록이 바로 고쳐집니다.',
};

/** 양식별 문서 내용 */
function DocumentBody({ doc }) {
  const b = doc.body || {};
  let rows = [];
  if (doc.form.code === 'LEAVE') {
    const deducted = b.leaveType === 'ANNUAL' || b.leaveType === 'HALF_AM' || b.leaveType === 'HALF_PM';
    rows = [
      ['휴가 종류', LEAVE_TYPE_NAMES[b.leaveType] || b.leaveType],
      [
        '기간',
        b.startDate === b.endDate ? formatDay(b.startDate) : `${formatDay(b.startDate)} ~ ${formatDay(b.endDate)}`,
      ],
      ['차감 일수', deducted ? `${formatDays(b.usedDays)} (주말·공휴일 제외)` : '연차에서 빼지 않음'],
      ['업무 인수자', b.handover ? `${b.handover.name} ${b.handover.title || ''}` : '지정 안 함'],
      ['사유', b.reason || '-'],
    ];
  } else if (doc.form.code === 'ATTENDANCE_FIX') {
    // 정정 전 값(before)은 상신 때 서버가 채움 (M3 명세 5장)
    const before = b.before || {};
    const change = (from, to) => (to ? `${from || '기록 없음'} → ${to}` : `${from || '기록 없음'} (그대로)`);
    rows = [
      ['정정할 날짜', formatDay(b.workDate)],
      ['출근', change(before.clockIn, (b.clockIn || '').slice(0, 5))],
      ['퇴근', change(before.clockOut, (b.clockOut || '').slice(0, 5))],
      ['정정 전 판정', CELL_META[before.code]?.label || '-'],
      ['사유', b.reason || '-'],
    ];
  } else if (doc.form.code === 'OVERTIME') {
    rows = [
      ['근무일', formatDay(b.overtimeDate)],
      [
        '시간',
        `${b.startTime} ~ ${b.endTime}${hoursBetween(b.startTime, b.endTime) ? ` (${hoursBetween(b.startTime, b.endTime)})` : ''}`,
      ],
      ['업무 내용', b.reason || '-'],
    ];
  } else {
    rows = [
      ['금액', formatAmount(b.amount)],
      ['내용', b.content || '-'],
    ];
  }
  return (
    <dl className="divide-y divide-gray-100">
      {rows.map(([label, value]) => (
        <div key={label} className="grid grid-cols-[96px_1fr] gap-4 py-2.5 text-sm">
          <dt className="text-gray-500">{label}</dt>
          <dd className="whitespace-pre-wrap text-gray-900">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

const STAMP = {
  APPROVED: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  REJECTED: 'border-red-200 bg-red-50 text-red-700',
  PENDING: 'border-blue-200 bg-blue-50 text-blue-700',
  WAITING: 'border-gray-200 bg-white text-gray-500',
  SKIPPED: 'border-dashed border-gray-200 bg-gray-50 text-gray-400',
};

/** 결재선 한 줄 */
function LineRow({ line, docStatus }) {
  const withdrawn = docStatus === 'WITHDRAWN' && line.status === 'PENDING';
  const meta = withdrawn ? { label: '회수됨', tone: 'amber' } : LINE_STATUS_META[line.status] || { label: line.status };
  return (
    <li className={cn('flex gap-3 py-3', line.status === 'SKIPPED' && 'opacity-70')}>
      <span className="mt-0.5 w-10 shrink-0 text-xs font-medium text-gray-400">결재 {line.order}</span>
      <Avatar name={line.approver?.name || '?'} size="sm" />
      <div className="min-w-0 flex-1">
        <p className="text-sm">
          <PersonText person={line.approver} />
        </p>
        <p className="mt-0.5 text-xs text-gray-500">
          {line.source === 'DRAFTER' ? '기안자가 지정' : line.rule}
          {line.skipReason && ` · ${SKIP_REASON_LABELS[line.skipReason]}`}
        </p>
        {line.comment && (
          <p className="mt-1.5 rounded-md bg-gray-50 px-2.5 py-1.5 text-[13px] text-gray-700">“{line.comment}”</p>
        )}
      </div>
      <div className="shrink-0 text-right">
        <span
          className={cn(
            'inline-block rounded-md border px-2 py-0.5 text-xs font-medium',
            withdrawn ? 'border-amber-200 bg-amber-50 text-amber-700' : STAMP[line.status],
          )}
        >
          {meta.label}
        </span>
        {line.actedAt && <p className="mt-1 text-xs tabular-nums text-gray-400">{formatDateTime(line.actedAt)}</p>}
      </div>
    </li>
  );
}

export default function ApprovalDocumentPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const refreshCounts = useApprovalStore((s) => s.refresh);
  const [doc, setDoc] = useState(null);
  const [error, setError] = useState('');
  const [modal, setModal] = useState(null); // 'approve' | 'reject'
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [modalError, setModalError] = useState('');

  const load = useCallback(async () => {
    try {
      setDoc(await approvalDocumentApi.get(id));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, [id]);

  useEffect(() => {
    setDoc(null);
    load();
  }, [load]);

  const afterAction = (updated, message) => {
    setDoc(updated);
    toast.success(message);
    refreshCounts();
  };

  /** 처리 중 다른 사람이 먼저 처리했으면 최신 상태로 다시 그림 */
  const onActionError = async (err) => {
    if (getErrorCode(err) === 'DOCUMENT_STATE_CONFLICT') {
      toast.error(getErrorMessage(err));
      setModal(null);
      await load();
      refreshCounts();
      return;
    }
    setModalError(getErrorMessage(err));
  };

  const openModal = (kind) => {
    setComment('');
    setModalError('');
    setModal(kind);
  };

  const pending = doc?.line.find((l) => l.status === 'PENDING');
  const next = doc?.line.find((l) => l.status === 'WAITING');

  const submitModal = async () => {
    setBusy(true);
    try {
      if (modal === 'approve') {
        const updated = await approvalDocumentApi.approve(doc.id, comment.trim() || undefined);
        afterAction(
          updated,
          updated.status === 'APPROVED'
            ? '최종 승인했습니다. 기안자에게 알림을 보냈습니다.'
            : `승인했습니다. 다음 결재자 ${next?.approver.name} ${next?.approver.title}에게 결재 요청 알림을 보냈습니다.`,
        );
      } else {
        const updated = await approvalDocumentApi.reject(doc.id, comment.trim());
        afterAction(updated, `반려했습니다. 기안자 ${doc.drafter.name}님에게 반려 사유와 함께 알림을 보냈습니다.`);
      }
      setModal(null);
    } catch (err) {
      await onActionError(err);
    } finally {
      setBusy(false);
    }
  };

  const withdraw = async () => {
    const ok = await confirm({
      title: '이 문서를 회수할까요?',
      description:
        '아무도 결재하지 않은 동안만 회수할 수 있습니다. 회수하면 결재자의 결재함에서 빠지고, 예약한 연차는 돌려받습니다.',
      confirmText: '회수',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      afterAction(await approvalDocumentApi.withdraw(doc.id), '문서를 회수했습니다.');
    } catch (err) {
      toast.error(getErrorMessage(err));
      load();
    }
  };

  const resubmit = async () => {
    try {
      const copy = await approvalDocumentApi.resubmit(doc.id);
      toast.success('내용을 복사한 새 문서를 만들었습니다. 고쳐서 다시 올려 주세요.');
      navigate(`/approvals/new?draft=${copy.id}`);
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  if (error) {
    return (
      <EmptyState
        icon={FileX2}
        title="문서를 열 수 없습니다"
        description={error}
        action={
          <Button variant="secondary" onClick={() => navigate('/approvals')}>
            결재함으로
          </Button>
        }
      />
    );
  }
  if (!doc) return <PageLoader />;

  const { myActions } = doc;
  const approvers = doc.line.filter((l) => l.status !== 'SKIPPED');

  return (
    <>
      <Link
        to="/approvals"
        className="mb-4 inline-flex items-center gap-1 text-[13px] text-gray-500 hover:text-gray-800"
      >
        <ArrowLeft size={14} /> 결재함
      </Link>

      <Card className="mb-4">
        <div className="flex flex-col gap-4 p-5 lg:flex-row lg:items-start">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="gray">{doc.form.name}</Badge>
              {doc.docNo && <span className="text-[13px] tabular-nums text-gray-500">{doc.docNo}</span>}
              <DocStatusBadge status={doc.status} />
            </div>
            <h1 className="mt-2 text-xl font-semibold text-gray-900">{doc.title}</h1>
            <p className="mt-1 text-[13px] text-gray-500">
              {doc.drafter.name} · {doc.drafter.department} {doc.drafter.title}
              {doc.submittedAt && ` · ${formatDateTime(doc.submittedAt)} 상신`}
              {doc.completedAt && ` · ${formatDateTime(doc.completedAt)} 완료`}
            </p>
            {doc.resubmittedFromId && (
              <p className="mt-1 text-[13px] text-gray-500">
                다시 올린 문서입니다.{' '}
                <Link to={`/approvals/${doc.resubmittedFromId}`} className="text-blue-700 hover:underline">
                  원래 문서 보기
                </Link>
              </p>
            )}
          </div>
          <div className="flex shrink-0 flex-wrap gap-2">
            {myActions.canReject && (
              <Button variant="danger-outline" icon={X} onClick={() => openModal('reject')}>
                반려
              </Button>
            )}
            {myActions.canApprove && (
              <Button icon={Check} onClick={() => openModal('approve')}>
                승인
              </Button>
            )}
            {myActions.canWithdraw && (
              <Button variant="secondary" icon={CornerUpLeft} onClick={withdraw}>
                회수
              </Button>
            )}
            {myActions.canResubmit && (
              <Button icon={RotateCcw} onClick={resubmit}>
                고쳐서 다시 올리기
              </Button>
            )}
          </div>
        </div>
        {pending && doc.status === 'IN_PROGRESS' && !myActions.canApprove && (
          <p className="border-t border-gray-100 px-5 py-3 text-[13px] text-gray-600">
            지금{' '}
            <b className="font-semibold text-gray-900">
              {pending.approver.name} {pending.approver.title}
            </b>
            의 결재 차례입니다.
          </p>
        )}
      </Card>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <div className="space-y-4 lg:col-span-8">
          <Card>
            <CardHeader
              title="결재선"
              description={
                doc.line.some((l) => l.source === 'DRAFTER')
                  ? '기안자가 지정한 결재선'
                  : `규칙으로 자동 생성 · ${approvers.map((l) => l.rule).join(' → ') || '결재자 없음'}`
              }
            />
            <div className="px-5">
              <ul className="divide-y divide-gray-100">
                <li className="flex gap-3 py-3">
                  <span className="mt-0.5 w-10 shrink-0 text-xs font-medium text-gray-400">기안</span>
                  <Avatar name={doc.drafter.name} size="sm" />
                  <div className="min-w-0 flex-1 text-sm">
                    <PersonText person={doc.drafter} />
                  </div>
                  <div className="shrink-0 text-right">
                    <span className="inline-block rounded-md border border-gray-200 px-2 py-0.5 text-xs font-medium text-gray-600">
                      {doc.status === 'DRAFT' ? '작성 중' : '상신'}
                    </span>
                    {doc.submittedAt && (
                      <p className="mt-1 text-xs tabular-nums text-gray-400">{formatDateTime(doc.submittedAt)}</p>
                    )}
                  </div>
                </li>
                {doc.line.map((l) => (
                  <LineRow key={l.order} line={l} docStatus={doc.status} />
                ))}
              </ul>
              {doc.line.length === 0 && doc.status === 'APPROVED' && (
                <p className="pb-4 text-[13px] text-gray-500">결재할 사람이 남지 않아 상신과 동시에 승인되었습니다.</p>
              )}
            </div>
            {doc.references.length > 0 && (
              <div className="border-t border-gray-100 px-5 py-3">
                <p className="mb-1.5 text-xs font-medium text-gray-400">참조</p>
                <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
                  {doc.references.map((r) => (
                    <li key={r.id}>
                      <PersonText person={r} />
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </Card>

          <Card>
            <CardHeader title="문서 내용" />
            <div className="px-5 py-2">
              <DocumentBody doc={doc} />
            </div>
          </Card>
        </div>

        <Card className="h-fit lg:col-span-4">
          <CardHeader title="결재 이력" />
          {doc.history.length === 0 ? (
            <p className="px-5 py-6 text-[13px] text-gray-500">아직 기록이 없습니다.</p>
          ) : (
            <ol className="space-y-4 px-5 py-4">
              {doc.history.map((h, i) => (
                <li key={i} className="relative pl-4">
                  <span className="absolute left-0 top-1.5 h-1.5 w-1.5 rounded-full bg-gray-300" />
                  <p className="text-[13px] text-gray-900">
                    {h.actor ? <b className="font-semibold">{h.actor.name}</b> : '시스템'}{' '}
                    {HISTORY_ACTION_LABELS[h.action] || h.action}
                  </p>
                  {h.comment && <p className="mt-0.5 text-[13px] text-gray-600">{h.comment}</p>}
                  <p className="mt-0.5 text-xs tabular-nums text-gray-400">{formatDateTime(h.at)}</p>
                </li>
              ))}
            </ol>
          )}
        </Card>
      </div>

      <Modal
        open={modal === 'approve'}
        onClose={() => setModal(null)}
        title="이 문서를 승인할까요?"
        description={
          next
            ? `승인하면 다음 결재자 ${next.approver.name} ${next.approver.title}에게 넘어갑니다.`
            : `마지막 결재입니다. ${FINAL_APPROVE_EFFECT[doc.form.code] || '승인하면 결재가 끝납니다.'}`
        }
        footer={
          <>
            <Button variant="secondary" onClick={() => setModal(null)}>
              취소
            </Button>
            <Button icon={Check} loading={busy} onClick={submitModal}>
              승인
            </Button>
          </>
        }
      >
        <Field label="의견 (선택)" htmlFor="approve-comment" error={modalError || undefined}>
          <Textarea id="approve-comment" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
        </Field>
      </Modal>

      <Modal
        open={modal === 'reject'}
        onClose={() => setModal(null)}
        title="이 문서를 반려할까요?"
        description="반려하면 결재가 끝나고, 기안자는 내용을 고쳐 다시 올릴 수 있습니다."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModal(null)}>
              취소
            </Button>
            <Button variant="danger" icon={X} loading={busy} disabled={!comment.trim()} onClick={submitModal}>
              반려
            </Button>
          </>
        }
      >
        <Field
          label="반려 사유"
          htmlFor="reject-comment"
          required
          hint="반려 사유는 기안자와 이전 결재자에게 보입니다."
          error={modalError || undefined}
        >
          <Textarea id="reject-comment" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
        </Field>
      </Modal>
    </>
  );
}
