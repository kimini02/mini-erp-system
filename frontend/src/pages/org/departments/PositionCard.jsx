import { useEffect, useState } from 'react';
import { positionApi } from '@/api/org';
import { getErrorMessage } from '@/lib/errors';
import { confirm } from '@/lib/confirm';
import { toast } from '@/lib/toast';
import PositionCardView from './PositionCardView';
import { movePosition, positionOrder, POSITION_ORDER_NOTICE } from './positionOrder';

export default function PositionCard({ canCreate, canUpdate, canDelete }) {
  const [saved, setSaved] = useState(null);
  const [positions, setPositions] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [name, setName] = useState('');
  const [editing, setEditing] = useState(null);
  const [needsOrderSave, setNeedsOrderSave] = useState(false);
  const dirty =
    needsOrderSave || (positions !== null && positions.some((item, index) => item.id !== saved?.[index]?.id));

  const accept = (rows) => {
    // 서버의 rankOrder,id 순서를 그대로 사용한다. 미저장 순서는 컴포넌트 안에만 둔다.
    setSaved(rows);
    setPositions(rows);
  };

  useEffect(() => {
    let active = true;
    positionApi
      .list()
      .then((rows) => {
        if (active) {
          setSaved(rows);
          setPositions(rows);
        }
      })
      .catch((err) => {
        if (active) setError(getErrorMessage(err));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const reload = async () => {
    setLoading(true);
    try {
      accept(await positionApi.list());
      setError('');
    } catch (err) {
      // 쓰기는 성공했지만 재조회가 실패했다면 옛 목록으로 다시 쓰지 않게 한다.
      setPositions(null);
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const mutate = async (request, success) => {
    setBusy(true);
    setError('');
    try {
      await request();
      success();
      await reload();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const create = async (event) => {
    event.preventDefault();
    if (!canCreate || busy || loading || dirty || !name.trim()) return;
    await mutate(
      () => positionApi.create({ name: name.trim() }),
      () => {
        setName('');
        setNotice(
          canUpdate ? POSITION_ORDER_NOTICE : `${POSITION_ORDER_NOTICE} 순서 변경 권한이 있는 관리자에게 요청하세요.`,
        );
        setNeedsOrderSave(canUpdate);
      },
    );
  };

  const rename = async (event) => {
    event.preventDefault();
    if (!canUpdate || busy || !editing?.name.trim()) return;
    await mutate(
      () => positionApi.update(editing.id, { name: editing.name.trim() }),
      () => {
        setEditing(null);
        toast.success('직급 이름을 바꿨습니다.');
      },
    );
  };

  const remove = async (position) => {
    if (!canDelete || busy || dirty) return;
    setBusy(true);
    const ok = await confirm({
      title: `'${position.name}' 직급을 삭제할까요?`,
      description: '이 직급을 쓰는 사원이 있으면 삭제할 수 없습니다.',
      confirmText: '삭제',
      tone: 'danger',
    });
    if (!ok) {
      setBusy(false);
      return;
    }
    await mutate(
      () => positionApi.remove(position.id),
      () => toast.success('직급을 삭제했습니다.'),
    );
  };

  const saveOrder = async () => {
    if (!canUpdate || busy || loading || !dirty || !positions) return;
    setBusy(true);
    setError('');
    try {
      accept(await positionApi.saveOrder(positionOrder(positions)));
      setNeedsOrderSave(false);
      setNotice('');
      toast.success('직급 순서를 저장했습니다.');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <PositionCardView
      positions={positions}
      canCreate={canCreate}
      canUpdate={canUpdate}
      canDelete={canDelete}
      busy={busy}
      loading={loading}
      error={error}
      notice={notice}
      dirty={dirty}
      name={name}
      onNameChange={setName}
      editing={editing}
      onEdit={setEditing}
      onEditNameChange={(value) => setEditing((item) => ({ ...item, name: value }))}
      onCancelEdit={() => setEditing(null)}
      onRename={rename}
      onCreate={create}
      onMove={(index, direction) => setPositions((rows) => movePosition(rows, index, direction))}
      onSaveOrder={saveOrder}
      onCancelOrder={() => {
        setPositions(saved);
        setNeedsOrderSave(false);
        setNotice('');
      }}
      onRemove={remove}
      onReload={reload}
    />
  );
}
