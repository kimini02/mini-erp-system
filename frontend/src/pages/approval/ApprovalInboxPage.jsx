import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, FileCheck2, FilePlus2, Search } from 'lucide-react';
import { approvalFormApi, approvalInboxApi } from '@/api/approval';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { MENU } from '@/lib/permissions';
import { INBOX_BOXES, formatDateTime } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/cn';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Input,
  PageHeader,
  PageLoader,
  Select,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
} from '@/components/ui';
import { DocStatusBadge, StepDots } from '@/components/approval/ApprovalBits';

const PAGE_SIZE = 15;

function useDebounced(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

const EMPTY_TEXT = {
  todo: '지금 결재할 문서가 없습니다.',
  'in-progress': '내가 승인한 뒤 진행 중인 문서가 없습니다.',
  done: '결재가 끝난 문서가 없습니다.',
  mine: '올린 문서가 없습니다.',
  drafts: '임시 저장한 문서가 없습니다.',
  reference: '참조로 받은 문서가 없습니다.',
};

export default function ApprovalInboxPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const box = INBOX_BOXES.some((b) => b.key === searchParams.get('box')) ? searchParams.get('box') : 'todo';
  const canDraft = useAuthStore((s) => s.can(MENU.APPROVAL_DRAFT, 'C'));
  const counts = useApprovalStore((s) => s.counts);
  const refreshCounts = useApprovalStore((s) => s.refresh);

  const [forms, setForms] = useState([]);
  const [formCode, setFormCode] = useState('');
  const [q, setQ] = useState('');
  const debouncedQ = useDebounced(q.trim(), 300);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    approvalFormApi
      .list()
      .then(setForms)
      .catch(() => {});
    refreshCounts();
  }, [refreshCounts]);

  useEffect(() => setPage(0), [box, formCode, debouncedQ]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setResult(
        await approvalInboxApi.list({
          box,
          formCode: formCode || undefined,
          q: debouncedQ || undefined,
          page,
          size: PAGE_SIZE,
        }),
      );
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [box, formCode, debouncedQ, page]);

  useEffect(() => {
    load();
  }, [load]);

  const selectBox = (key) => setSearchParams(key === 'todo' ? {} : { box: key });
  const open = (row) => navigate(row.status === 'DRAFT' ? `/approvals/new?draft=${row.id}` : `/approvals/${row.id}`);

  const rows = result?.content || [];
  const pageInfo = result?.page;
  const isDrafts = box === 'drafts';

  return (
    <>
      <PageHeader
        title="결재함"
        description="결재할 문서와 내가 올린 문서를 한곳에서 확인합니다."
        actions={
          canDraft && (
            <Button icon={FilePlus2} onClick={() => navigate('/approvals/new')}>
              기안하기
            </Button>
          )
        }
      />

      <Card className="overflow-hidden">
        <div
          role="tablist"
          aria-label="결재함"
          className="flex gap-1 overflow-x-auto border-b border-gray-100 px-3 pt-2"
        >
          {INBOX_BOXES.map((b) => {
            const active = b.key === box;
            const count = counts[b.key];
            return (
              <button
                key={b.key}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => selectBox(b.key)}
                className={cn(
                  '-mb-px flex shrink-0 items-center gap-1.5 border-b-2 px-3 pb-2.5 pt-1.5 text-[13px] font-medium transition-colors',
                  active ? 'border-blue-600 text-blue-700' : 'border-transparent text-gray-500 hover:text-gray-800',
                )}
              >
                {b.label}
                {count > 0 && (
                  <span
                    className={cn(
                      'rounded-full px-1.5 text-[11px] tabular-nums',
                      b.key === 'todo' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600',
                    )}
                  >
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="flex flex-col gap-2 border-b border-gray-100 p-4 sm:flex-row">
          <label className="sm:w-72">
            <span className="sr-only">문서 검색</span>
            <Input
              icon={Search}
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="제목 또는 문서번호"
            />
          </label>
          <label className="sm:w-44">
            <span className="sr-only">양식</span>
            <Select value={formCode} onChange={(e) => setFormCode(e.target.value)}>
              <option value="">전체 양식</option>
              {forms.map((f) => (
                <option key={f.code} value={f.code}>
                  {f.name}
                </option>
              ))}
            </Select>
          </label>
        </div>

        {error ? (
          <EmptyState icon={FileCheck2} title="결재함을 불러오지 못했습니다" description={error} />
        ) : !result ? (
          <PageLoader />
        ) : (
          <Table className={cn(loading && 'opacity-60')}>
            <thead>
              <tr>
                <Th>문서번호</Th>
                <Th>양식</Th>
                <Th>제목</Th>
                <Th>기안자</Th>
                <Th>{isDrafts ? '저장일' : '기안일'}</Th>
                {!isDrafts && <Th>결재 진행</Th>}
                <Th>상태</Th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <TableEmpty colSpan={isDrafts ? 6 : 7}>
                  {q || formCode ? '조건에 맞는 문서가 없습니다.' : EMPTY_TEXT[box]}
                </TableEmpty>
              ) : (
                rows.map((r) => (
                  <Tr key={r.id} className="cursor-pointer" onClick={() => open(r)}>
                    <Td className="tabular-nums text-gray-500">{r.docNo || '-'}</Td>
                    <Td>
                      <Badge tone="gray">{r.formName}</Badge>
                    </Td>
                    <Td>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          open(r);
                        }}
                        className="text-left font-medium text-gray-900 hover:text-blue-700 hover:underline"
                      >
                        {r.title}
                      </button>
                    </Td>
                    <Td>
                      <span className="block text-gray-900">
                        {r.drafter.name} <span className="text-gray-500">{r.drafter.title}</span>
                      </span>
                      <span className="block text-xs text-gray-500">{r.drafter.department}</span>
                    </Td>
                    <Td className="tabular-nums text-gray-500">
                      {formatDateTime(isDrafts ? r.updatedAt : r.submittedAt)}
                    </Td>
                    {!isDrafts && (
                      <Td>
                        <span className="flex items-center gap-2">
                          <StepDots steps={r.steps} />
                          {r.currentApprover && (
                            <span className="text-xs text-gray-500">
                              {r.currentApprover.name} {r.currentApprover.title}
                            </span>
                          )}
                        </span>
                      </Td>
                    )}
                    <Td>
                      <DocStatusBadge status={r.status} />
                    </Td>
                  </Tr>
                ))
              )}
            </tbody>
          </Table>
        )}

        {pageInfo && pageInfo.totalElements > 0 && (
          <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3 text-[13px] text-gray-500">
            <span>
              총 <b className="font-semibold text-gray-900">{pageInfo.totalElements}</b>건 · {pageInfo.number + 1} /{' '}
              {pageInfo.totalPages} 쪽
            </span>
            <div className="flex gap-1">
              <Button
                variant="secondary"
                size="sm"
                icon={ChevronLeft}
                disabled={pageInfo.first}
                onClick={() => setPage((p) => p - 1)}
                aria-label="이전 쪽"
              />
              <Button
                variant="secondary"
                size="sm"
                icon={ChevronRight}
                disabled={pageInfo.last}
                onClick={() => setPage((p) => p + 1)}
                aria-label="다음 쪽"
              />
            </div>
          </div>
        )}
      </Card>
    </>
  );
}
