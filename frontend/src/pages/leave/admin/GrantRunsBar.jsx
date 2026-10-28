import { useCallback, useEffect, useState } from 'react';
import { History, Play } from 'lucide-react';
import { leaveAdminApi } from '@/api/leave';
import { formatDate, formatDateTime } from '@/lib/approval';
import { todayLocal } from '@/lib/attendance';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { Badge, Button, Card, Input, Modal } from '@/components/ui';

/** 연차 부여 배치: 마지막 실행 한 줄 + 지금 실행 (범위 ALL + 수정 권한). 지난 기록은 창으로 */
export default function GrantRunsBar() {
  const [runs, setRuns] = useState(null);
  const [date, setDate] = useState(todayLocal());
  const [running, setRunning] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const load = useCallback(
    () =>
      leaveAdminApi
        .grantRuns(10)
        .then(setRuns)
        .catch(() => setRuns([])),
    [],
  );
  useEffect(() => {
    load();
  }, [load]);

  const run = async () => {
    setRunning(true);
    try {
      const r = await leaveAdminApi.runGrant(date);
      // 실행 응답은 건수만 ({ processed, skipped }), 실행 기록 목록은 processedCount 로 옴
      const done = r.processedCount ?? r.processed;
      const skipped = r.skippedCount ?? r.skipped;
      toast.success(
        `부여 ${done}건${skipped ? ` · 건너뜀 ${skipped}건` : ''}. 같은 날짜로 다시 실행해도 두 번 부여되지 않습니다.`,
      );
      load();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setRunning(false);
    }
  };

  const last = runs?.[0];
  const statusBadge = (r) => (
    <Badge tone={r.status === 'SUCCESS' ? 'green' : 'red'}>{r.status === 'SUCCESS' ? '성공' : '실패'}</Badge>
  );

  return (
    <Card className="mb-4 flex flex-col gap-3 px-5 py-3.5 lg:flex-row lg:items-center">
      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-semibold text-gray-900">
          연차 자동 부여{' '}
          <span className="font-normal text-gray-500">
            · 매일 00:30에 자동으로 돌고, 놓친 날짜는 다음 실행이 채웁니다.
          </span>
        </p>
        <p className="mt-1 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-gray-500">
          {!runs ? (
            '불러오는 중…'
          ) : !last ? (
            '실행 기록이 없습니다.'
          ) : (
            <>
              마지막 실행 <span className="tabular-nums">{formatDateTime(last.finishedAt)}</span> {statusBadge(last)}
              <span>
                부여 {last.processedCount}건 · 건너뜀 {last.skippedCount}건
              </span>
              {last.message && <span className="text-amber-700">· {last.message}</span>}
            </>
          )}
        </p>
      </div>
      <div className="flex flex-wrap gap-2 lg:shrink-0">
        <Button variant="ghost" icon={History} disabled={!runs?.length} onClick={() => setHistoryOpen(true)}>
          기록 보기
        </Button>
        <Input
          type="date"
          aria-label="부여 기준 날짜"
          max={todayLocal()}
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />
        <Button variant="secondary" icon={Play} loading={running} onClick={run}>
          지금 실행
        </Button>
      </div>

      <Modal
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        title="연차 자동 부여 기록"
        description="최근 10번의 실행입니다."
        footer={
          <Button variant="secondary" onClick={() => setHistoryOpen(false)}>
            닫기
          </Button>
        }
      >
        <ul className="-mx-1 divide-y divide-gray-100">
          {(runs || []).map((r) => (
            <li key={r.id} className="px-1 py-3 text-[13px]">
              <p className="flex items-center justify-between gap-2">
                <span className="font-medium text-gray-900">{formatDate(r.targetDate)} 기준</span>
                {statusBadge(r)}
              </p>
              <p className="mt-0.5 text-xs text-gray-500">
                부여 {r.processedCount}건 · 건너뜀 {r.skippedCount}건 · {formatDateTime(r.finishedAt)}
              </p>
              {r.message && <p className="mt-0.5 text-xs text-amber-700">{r.message}</p>}
            </li>
          ))}
        </ul>
      </Modal>
    </Card>
  );
}
