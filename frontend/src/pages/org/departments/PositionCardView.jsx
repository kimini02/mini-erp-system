import { ArrowDown, ArrowUp, Pencil, Plus, Trash2 } from 'lucide-react';
import { Button, Card, CardBody, CardFooter, CardHeader, Field, Input, Spinner } from '@/components/ui';

export default function PositionCardView({
  positions,
  canCreate,
  canUpdate,
  canDelete,
  busy,
  loading,
  error,
  notice,
  dirty,
  name,
  onNameChange,
  editing,
  onEditNameChange,
  onEdit,
  onCancelEdit,
  onRename,
  onCreate,
  onMove,
  onSaveOrder,
  onCancelOrder,
  onRemove,
  onReload,
}) {
  const editingNow = editing !== null;
  const unavailable = busy || loading || positions === null;
  return (
    <Card className="min-w-0" aria-label="직급 관리">
      <CardHeader title="직급" description="위에서부터 낮은 직급 → 높은 직급 순서입니다. 회사 전체에 적용됩니다." />
      <CardBody className="space-y-4">
        {error && (
          <p role="alert" className="break-words text-sm text-red-600">
            {error}
          </p>
        )}
        {notice && (
          <p role="status" className="rounded-lg bg-blue-50 p-3 text-sm text-blue-800">
            {notice}
          </p>
        )}
        {loading ? (
          <p className="flex items-center gap-2 text-sm text-gray-500">
            <Spinner size={16} />
            직급을 불러오는 중입니다.
          </p>
        ) : positions === null ? (
          <Button variant="secondary" onClick={onReload}>
            다시 불러오기
          </Button>
        ) : positions.length === 0 ? (
          <p className="text-sm text-gray-500">등록된 직급이 없습니다.</p>
        ) : (
          <ol className="space-y-2" aria-label="낮은 직급부터 높은 직급 순서">
            {positions.map((position, index) => (
              <li key={position.id} className="min-w-0 rounded-lg border border-gray-200 p-3">
                {editing?.id === position.id ? (
                  <form onSubmit={onRename} className="space-y-2">
                    <Field label="직급 이름" htmlFor={`position-name-${position.id}`}>
                      <Input
                        id={`position-name-${position.id}`}
                        value={editing.name}
                        maxLength={100}
                        required
                        autoFocus
                        disabled={busy}
                        onChange={(event) => onEditNameChange(event.target.value)}
                      />
                    </Field>
                    <div className="flex flex-wrap justify-end gap-2">
                      <Button variant="secondary" size="sm" disabled={busy} onClick={onCancelEdit}>
                        이름 변경 취소
                      </Button>
                      <Button
                        size="sm"
                        type="submit"
                        loading={busy}
                        disabled={!editing.name.trim() || editing.name.trim() === position.name}
                      >
                        이름 저장
                      </Button>
                    </div>
                  </form>
                ) : (
                  <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-center">
                    <p className="min-w-0 flex-1 break-all text-sm font-medium text-gray-900">{position.name}</p>
                    <div className="flex flex-wrap items-center gap-1.5">
                      {canUpdate && (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            icon={ArrowUp}
                            aria-label={`${position.name} 위로 이동`}
                            disabled={unavailable || editingNow || index === 0}
                            onClick={() => onMove(index, -1)}
                          />
                          <Button
                            variant="ghost"
                            size="sm"
                            icon={ArrowDown}
                            aria-label={`${position.name} 아래로 이동`}
                            disabled={unavailable || editingNow || index === positions.length - 1}
                            onClick={() => onMove(index, 1)}
                          />
                          <Button
                            variant="secondary"
                            size="sm"
                            icon={Pencil}
                            aria-label={`${position.name} 이름 변경`}
                            disabled={unavailable || editingNow || dirty}
                            onClick={() => onEdit(position)}
                          >
                            이름 변경
                          </Button>
                        </>
                      )}
                      {canDelete && (
                        <Button
                          variant="danger-outline"
                          size="sm"
                          icon={Trash2}
                          aria-label={`${position.name} 삭제`}
                          disabled={unavailable || editingNow || dirty}
                          onClick={() => onRemove(position)}
                        >
                          삭제
                        </Button>
                      )}
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ol>
        )}
        {canCreate && (
          <form onSubmit={onCreate} className="space-y-2">
            <Field label="새 직급 이름" htmlFor="position-new-name">
              <Input
                id="position-new-name"
                value={name}
                maxLength={100}
                required
                disabled={unavailable || dirty || editingNow}
                onChange={(event) => onNameChange(event.target.value)}
              />
            </Field>
            <div className="flex justify-end">
              <Button
                type="submit"
                variant="secondary"
                size="sm"
                icon={Plus}
                disabled={unavailable || dirty || editingNow || !name.trim()}
              >
                직급 추가
              </Button>
            </div>
          </form>
        )}
        {canUpdate && (
          <p className="text-xs leading-relaxed text-gray-500">
            순서를 바꾼 뒤 저장해 주세요. 취소하거나 페이지를 떠나면 저장된 순서를 유지합니다. 이름 변경·추가·삭제는
            각각 즉시 반영됩니다.
          </p>
        )}
      </CardBody>
      {canUpdate && (
        <CardFooter className="flex-wrap">
          <Button variant="secondary" onClick={onCancelOrder} disabled={unavailable || !dirty || editingNow}>
            순서 취소
          </Button>
          <Button onClick={onSaveOrder} disabled={unavailable || !dirty || editingNow}>
            순서 저장
          </Button>
        </CardFooter>
      )}
    </Card>
  );
}
