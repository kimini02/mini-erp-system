import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { leaveAdminApi } from '@/api/leave';
import { LEDGER_TYPE_META, formatDate, formatDays, formatSignedDays } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/cn';
import { Badge, Button, Modal, PageLoader, Table, TableEmpty, Td, Th, Tr } from '@/components/ui';

/** 원장 보기 */
export default function LeaveLedgerModal({ row, year, onClose }) {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!row) return undefined;
    let alive = true;
    setRows(null);
    leaveAdminApi
      .ledger(row.user.id, year)
      .then((r) => alive && setRows(r))
      .catch((err) => alive && setError(getErrorMessage(err)));
    return () => {
      alive = false;
    };
  }, [row, year]);
  if (!row) return null;
  return (
    <Modal
      open
      onClose={onClose}
      size="xl"
      title={`${row.user.name}의 ${year}년 연차 원장`}
      description={`사용 가능 ${formatDays(row.available)} · 부여 ${formatDays(row.granted)} · 사용 ${formatDays(row.used)} · 결재 중 ${formatDays(row.reserved)}`}
      footer={
        <Button variant="secondary" onClick={onClose}>
          닫기
        </Button>
      }
    >
      {error ? (
        <p className="text-[13px] text-red-600">{error}</p>
      ) : !rows ? (
        <PageLoader />
      ) : (
        <div className="max-h-[60vh] overflow-y-auto">
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
              {rows.length === 0 ? (
                <TableEmpty colSpan={6}>기록이 없습니다.</TableEmpty>
              ) : (
                rows.map((l, i) => {
                  const meta = LEDGER_TYPE_META[l.type] || { label: l.type, tone: 'gray' };
                  return (
                    <Tr key={l.id ?? i}>
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
                          <span className="text-xs text-gray-400">-</span>
                        )}
                      </Td>
                      <Td
                        align="right"
                        className={cn('tabular-nums font-medium', l.days > 0 ? 'text-emerald-700' : 'text-red-600')}
                      >
                        {formatSignedDays(l.days)}
                      </Td>
                      <Td align="right" className="tabular-nums">
                        {formatDays(l.balance).replace('일', '')}
                      </Td>
                    </Tr>
                  );
                })
              )}
            </tbody>
          </Table>
        </div>
      )}
    </Modal>
  );
}
