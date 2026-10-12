import React from 'react';
import { StatusBadge, Table, Th, Tr, Td, TableEmpty } from '@/components/ui';
import { LEAVE_TYPE_LABELS } from '@/lib/labels';

const LeaveHistoryTable = ({ historyData = [] }) => {
  // 최신 신청이 위로 오도록
  const sortedHistory = [...historyData].reverse();

  return (
    <Table>
      <thead>
        <tr>
          <Th>신청일</Th>
          <Th>유형</Th>
          <Th>기간</Th>
          <Th align="right">일수</Th>
          <Th>사유</Th>
          <Th align="center">상태</Th>
          <Th>비고</Th>
        </tr>
      </thead>
      <tbody>
        {sortedHistory.length > 0 ? (
          sortedHistory.map((item) => (
            <Tr key={item.appId}>
              <Td className="tabular-nums text-gray-500">{item.createdAt?.split('T')[0] || '-'}</Td>
              <Td className="font-medium text-gray-900">{LEAVE_TYPE_LABELS[item.appType] || item.appType}</Td>
              <Td className="whitespace-nowrap tabular-nums">
                {item.startDate === item.endDate ? item.startDate : `${item.startDate} ~ ${item.endDate}`}
              </Td>
              <Td align="right" className="tabular-nums">{item.usedDays}일</Td>
              <Td className="max-w-[240px] truncate text-gray-500">{item.requestReason}</Td>
              <Td align="center"><StatusBadge status={item.appStatus} /></Td>
              <Td className="max-w-[200px] truncate text-[13px] text-gray-500">
                {item.appStatus === 'REJECTED' && item.rejectReason ? item.rejectReason : '-'}
              </Td>
            </Tr>
          ))
        ) : (
          <TableEmpty colSpan={7}>연차 신청 내역이 없습니다.</TableEmpty>
        )}
      </tbody>
    </Table>
  );
};

export default LeaveHistoryTable;
