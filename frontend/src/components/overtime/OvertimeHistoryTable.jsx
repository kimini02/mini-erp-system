import React from 'react';
import { StatusBadge, Table, Th, Tr, Td, TableEmpty } from '@/components/ui';

const OvertimeHistoryTable = ({ historyData = [] }) => (
  <Table>
    <thead>
      <tr>
        <Th>신청일</Th>
        <Th>특근일</Th>
        <Th>시간</Th>
        <Th>사유</Th>
        <Th align="center">상태</Th>
      </tr>
    </thead>
    <tbody>
      {historyData.length === 0 ? (
        <TableEmpty colSpan={5}>특근 신청 내역이 없습니다.</TableEmpty>
      ) : (
        historyData.map((item) => (
          <Tr key={item.id}>
            <Td className="tabular-nums text-gray-500">{item.createdAt ? item.createdAt.split('T')[0] : '-'}</Td>
            <Td className="font-medium tabular-nums text-gray-900">{item.overtimeDate}</Td>
            <Td className="whitespace-nowrap tabular-nums">
              {item.startTime?.substring(0, 5)} ~ {item.endTime?.substring(0, 5)}
            </Td>
            <Td className="max-w-[320px] truncate text-gray-500">{item.reason}</Td>
            <Td align="center"><StatusBadge status={item.status} /></Td>
          </Tr>
        ))
      )}
    </tbody>
  </Table>
);

export default OvertimeHistoryTable;
