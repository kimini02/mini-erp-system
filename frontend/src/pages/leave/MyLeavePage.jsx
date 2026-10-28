import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CalendarCheck2, CalendarClock, CalendarPlus, CalendarRange, CornerUpLeft, NotebookText } from 'lucide-react';
import { approvalDocumentApi, approvalInboxApi, leaveApi } from '@/api/m2';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { MENU } from '@/lib/permissions';
import { LEDGER_TYPE_META, formatDate, formatDays, formatSignedDays } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { confirm } from '@/lib/confirm';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  PageHeader,
  PageLoader,
  Select,
  StatCard,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
} from '@/components/ui';
import { DocStatusBadge } from '@/components/approval/ApprovalBits';

const TYPE_FILTERS = [
  { value: '', label: '전체' },
  { value: 'GRANT', label: '부여' },
  { value: 'USE', label: '사용' },
  { value: 'RESERVE', label: '예약' },
  { value: 'RELEASE', label: '예약 해제' },
  { value: 'ADJUST', label: '조정' },
];

/** 사용 · 결재 중 · 사용 가능 비율 막대 */
function BalanceBar({ balance }) {
  const total = Math.max(balance.granted, balance.used + balance.reserved + Math.max(balance.available, 0), 1);
  const parts = [
    { label: '사용', value: balance.used, className: 'bg-blue-600' },
    { label: '결재 중', value: balance.reserved, className: 'bg-amber-400' },
    { label: '사용 가능', value: Math.max(balance.available, 0), className: 'bg-emerald-400' },
  ];
  return (
    <div>
      <div className="flex h-2.5 overflow-hidden rounded-full bg-gray-100">
        {parts.map(
          (p) =>
            p.value > 0 && (
              <span key={p.label} className={p.className} style={{ width: `${(p.value / total) * 100}%` }} />
            ),
        )}
      </div>
      <ul className="mt-2 flex flex-wrap gap-4 text-xs text-gray-600">
        {parts.map((p) => (
          <li key={p.label} className="flex items-center gap-1.5">
            <span className={cn('h-2 w-2 rounded-full', p.className)} />
            {p.label} <b className="font-semibold tabular-nums text-gray-900">{formatDays(p.value)}</b>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function MyLeavePage() {
  const navigate = useNavigate();
  const canDraft = useAuthStore((s) => s.can(MENU.APPROVAL_DRAFT, 'C'));
  const canInbox = useAuthStore((s) => s.can(MENU.APPROVAL_INBOX));
  const refreshCounts = useApprovalStore((s) => s.refresh);
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const [type, setType] = useState('');
  const [balance, setBalance] = useState(null);
  const [ledger, setLedger] = useState(null);
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [b, l] = await Promise.all([leaveApi.balance(year), leaveApi.ledger(year, type)]);
      setBalance(b);
      setLedger(l);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, [year, type]);

  const loadRequests = useCallback(() => {
    if (!canInbox) return;
    approvalInboxApi
      .list({ box: 'mine', formCode: 'LEAVE', page: 0, size: 6 })
      .then((r) => setRequests(r.content || []))
      .catch(() => setRequests([]));
  }, [canInbox]);

  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const withdraw = async (doc) => {
    const ok = await confirm({
      title: `${doc.docNo}를 회수할까요?`,
      description: '아무도 결재하지 않은 동안만 회수할 수 있습니다. 예약한 연차는 바로 돌려받습니다.',
      confirmText: '회수',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await approvalDocumentApi.withdraw(doc.id);
      toast.success(
        `${doc.docNo}를 회수했습니다. 예약했던 연차를 돌려받았고, ${doc.currentApprover ? `${doc.currentApprover.name} ${doc.currentApprover.title}의 결재함에서도 빠졌습니다.` : '결재함에서도 빠졌습니다.'}`,
      );
      load();
      loadRequests();
      refreshCounts();
    } catch (err) {
      toast.error(getErrorMessage(err));
      loadRequests();
    }
  };

  if (error) return <EmptyState icon={NotebookText} title="연차 정보를 불러오지 못했습니다" description={error} />;
  if (!balance || !ledger) return <PageLoader />;

  // 아직 아무도 결재하지 않은 진행 중 문서만 회수 가능 (진행 점: 기안 다음이 모두 대기·건너뜀)
  const withdrawable = (r) => r.status === 'IN_PROGRESS' && !r.steps.slice(1).includes('DONE');

  return (
    <>
      <PageHeader
        title="내 연차"
        description={`${year}년 연차를 얼마나 받고 썼는지, 그 기록을 한 줄씩 보여줍니다.`}
        actions={
          <div className="flex gap-2">
            <label>
              <span className="sr-only">연도</span>
              <Select value={year} onChange={(e) => setYear(Number(e.target.value))}>
                {[thisYear, thisYear - 1, thisYear - 2].map((y) => (
                  <option key={y} value={y}>
                    {y}년
                  </option>
                ))}
              </Select>
            </label>
            {canDraft && (
              <Button icon={CalendarPlus} onClick={() => navigate('/approvals/new?form=LEAVE')}>
                휴가 신청
              </Button>
            )}
          </div>
        }
      />

      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatCard
          label="올해 부여"
          value={formatDays(balance.granted).replace('일', '')}
          unit="일"
          icon={CalendarRange}
        />
        <StatCard label="사용" value={formatDays(balance.used).replace('일', '')} unit="일" icon={CalendarCheck2} />
        <StatCard
          label="결재 중 (예약)"
          value={formatDays(balance.reserved).replace('일', '')}
          unit="일"
          icon={CalendarClock}
          tone={balance.reserved > 0 ? 'amber' : 'gray'}
        />
        <StatCard
          label="사용 가능"
          value={formatDays(balance.available).replace('일', '')}
          unit="일"
          icon={NotebookText}
          tone="blue"
        />
      </div>

      <Card className="mt-4 p-5">
        <BalanceBar balance={balance} />
      </Card>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-12">
        <Card className="overflow-hidden lg:col-span-8">
          <CardHeader
            title="연차 원장"
            description="기록은 고치지 않고 쌓기만 합니다. 취소도 반대 기록으로 남깁니다."
          />
          <div className="flex flex-wrap gap-1.5 border-b border-gray-100 px-5 py-3">
            {TYPE_FILTERS.map((f) => (
              <button
                key={f.value}
                type="button"
                onClick={() => setType(f.value)}
                className={cn(
                  'h-7 rounded-full px-3 text-xs font-medium transition-colors',
                  type === f.value ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
                )}
              >
                {f.label}
              </button>
            ))}
          </div>
          <Table>
            <thead>
              <tr>
                <Th>날짜</Th>
                <Th>구분</Th>
                <Th>내용</Th>
                <Th>문서</Th>
                <Th align="right">증감</Th>
                <Th align="right">잔여</Th>
              </tr>
            </thead>
            <tbody>
              {ledger.length === 0 ? (
                <TableEmpty colSpan={6}>
                  {type ? '이 구분의 기록이 없습니다.' : `${year}년 기록이 없습니다.`}
                </TableEmpty>
              ) : (
                ledger.map((l) => {
                  const meta = LEDGER_TYPE_META[l.type] || { label: l.type, tone: 'gray' };
                  return (
                    <Tr key={l.id ?? `${l.date}-${l.type}-${l.balance}`}>
                      <Td className="tabular-nums text-gray-500">{formatDate(l.date)}</Td>
                      <Td>
                        <Badge tone={meta.tone}>{meta.label}</Badge>
                      </Td>
                      <Td className="text-gray-900">{l.memo || '-'}</Td>
                      <Td>
                        {l.document ? (
                          <Link
                            to={`/approvals/${l.document.id}`}
                            className="tabular-nums text-blue-700 hover:underline"
                          >
                            {l.document.docNo}
                          </Link>
                        ) : (
                          <span className="text-xs text-gray-400">자동</span>
                        )}
                      </Td>
                      <Td
                        align="right"
                        className={cn(
                          'font-medium tabular-nums',
                          l.days > 0 ? 'text-emerald-700' : l.days < 0 ? 'text-red-600' : 'text-gray-500',
                        )}
                      >
                        {formatSignedDays(l.days)}
                      </Td>
                      <Td align="right" className="tabular-nums text-gray-900">
                        {formatDays(l.balance).replace('일', '')}
                      </Td>
                    </Tr>
                  );
                })
              )}
            </tbody>
          </Table>
          <p className="border-t border-gray-100 px-5 py-3 text-[13px] text-gray-500">
            원장 합계와 사용 가능 일수는 항상 같습니다.{' '}
            <b className="font-semibold text-gray-900">합계 {formatDays(balance.available)}</b>
          </p>
        </Card>

        <Card className="h-fit lg:col-span-4">
          <CardHeader
            title="신청 내역"
            action={
              canInbox && (
                <Link to="/approvals?box=mine" className="text-[13px] text-blue-700 hover:underline">
                  전체 보기
                </Link>
              )
            }
          />
          {requests.length === 0 ? (
            <p className="px-5 py-6 text-[13px] text-gray-500">올린 휴가 신청이 없습니다.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {requests.map((r) => (
                <li key={r.id} className="px-5 py-3">
                  <div className="flex items-start justify-between gap-2">
                    <Link
                      to={`/approvals/${r.id}`}
                      className="min-w-0 text-[13px] font-medium text-gray-900 hover:text-blue-700 hover:underline"
                    >
                      {r.title}
                    </Link>
                    <DocStatusBadge status={r.status} />
                  </div>
                  <p className="mt-0.5 text-xs text-gray-500">
                    <span className="tabular-nums">{r.docNo}</span>
                    {r.currentApprover && ` · ${r.currentApprover.name} ${r.currentApprover.title} 결재 차례`}
                  </p>
                  {withdrawable(r) && (
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={CornerUpLeft}
                      className="-ml-2 mt-1"
                      onClick={() => withdraw(r)}
                    >
                      회수하기
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
          <p className="border-t border-gray-100 px-5 py-3 text-xs text-gray-500">
            아무도 결재하지 않은 동안만 회수할 수 있습니다.
          </p>
        </Card>
      </div>
    </>
  );
}
