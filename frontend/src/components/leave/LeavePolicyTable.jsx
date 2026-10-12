import React, { useState, useEffect } from 'react';
import { Info } from 'lucide-react';
import api from '../../api/axios';
import { Badge, Card, CardHeader, CardBody, Table, Th, Tr, Td } from '@/components/ui';

const NOTICES = [
  '연차는 입사일 기준으로 부여됩니다.',
  '반차는 오전(09:00~13:00), 오후(13:00~18:00)로 구분됩니다.',
  '당일/익일 신청은 관리자 승인이 필요합니다.',
  '미사용 연차는 다음 해로 이월되지 않습니다.',
];

const LeavePolicyTable = ({ position = '' }) => {
  const [policies, setPolicies] = useState([]);

  useEffect(() => {
    const fetchPolicy = async () => {
      try {
        // 백엔드: GET /api/v1/leave/policy → { position: "사원", annualLeaveDays: 15 }
        const response = await api.get('/leave/policy');
        if (response.data.success) {
          setPolicies(response.data.data);
        }
      } catch (error) {
        console.error('연차 정책 로드 실패:', error);
      }
    };
    fetchPolicy();
  }, []);

  return (
    <Card>
      <CardHeader title="직급별 연차 기준" />
      <Table>
        <thead>
          <tr>
            <Th>직급</Th>
            <Th align="right">연간 연차</Th>
            <Th align="center">비고</Th>
          </tr>
        </thead>
        <tbody>
          {[...policies]
            .sort((a, b) => b.annualLeaveDays - a.annualLeaveDays)
            .map((policy) => {
              const isMyRank = policy.position === position;
              return (
                <Tr key={policy.position} className={isMyRank ? 'bg-blue-50/60' : undefined}>
                  <Td className="font-medium text-gray-900">{policy.position}</Td>
                  <Td align="right" className="tabular-nums">{policy.annualLeaveDays}일</Td>
                  <Td align="center">{isMyRank && <Badge tone="blue">내 직급</Badge>}</Td>
                </Tr>
              );
            })}
        </tbody>
      </Table>
      <CardBody className="border-t border-gray-100">
        <p className="mb-2 flex items-center gap-1.5 text-[13px] font-medium text-gray-700">
          <Info size={14} className="text-gray-400" /> 유의사항
        </p>
        <ul className="list-disc space-y-1 pl-5 text-[13px] text-gray-500">
          {NOTICES.map((n) => <li key={n}>{n}</li>)}
        </ul>
      </CardBody>
    </Card>
  );
};

export default LeavePolicyTable;
