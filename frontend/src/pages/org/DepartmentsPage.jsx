import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, FolderTree, Move, Plus, Trash2, Users } from 'lucide-react';
import { departmentApi, workScheduleApi } from '@/api/org';
import { userApi } from '@/api/users';
import { useAuthStore } from '@/store/authStore';
import { MENU, USER_STATUS_META } from '@/lib/permissions';
import { getErrorCode, getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { confirm } from '@/lib/confirm';
import {
  Avatar,
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  Field,
  Input,
  PageHeader,
  PageLoader,
  Spinner,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
} from '@/components/ui';
import WorkScheduleCard from './departments/WorkScheduleCard';
import TreeNode from './departments/TreeNode';
import DepartmentFormModal from './departments/DepartmentFormModal';
import DepartmentMoveModal from './departments/DepartmentMoveModal';
import DepartmentHeadModal from './departments/DepartmentHeadModal';

export default function DepartmentsPage() {
  const navigate = useNavigate();
  const can = useAuthStore((s) => s.can);
  const canCreate = can(MENU.DEPARTMENT, 'C');
  const canUpdate = can(MENU.DEPARTMENT, 'U');
  const canDelete = can(MENU.DEPARTMENT, 'D');
  const canSeeEmployees = can(MENU.EMPLOYEE, 'V');

  const [tree, setTree] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [collapsed, setCollapsed] = useState(() => new Set());
  const [detail, setDetail] = useState(null);
  const [members, setMembers] = useState([]);
  const [membersError, setMembersError] = useState('');
  const [defaultSchedule, setDefaultSchedule] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [name, setName] = useState('');
  const [savingName, setSavingName] = useState(false);
  const [modal, setModal] = useState(null); // 'add' | 'move' | 'head'

  const loadTree = useCallback(async () => {
    const t = await departmentApi.tree();
    setTree(t);
    return t;
  }, []);

  const loadDetail = useCallback(
    async (id) => {
      if (!id) return;
      setDetailLoading(true);
      try {
        const d = await departmentApi.get(id);
        setDetail(d);
        setName(d.name);
        if (canSeeEmployees) {
          try {
            const list = await userApi.list({ departmentId: id, size: 100 });
            setMembers(list.content.filter((m) => m.status !== 'RETIRED'));
            setMembersError('');
          } catch (err) {
            setMembers([]);
            setMembersError(
              getErrorCode(err) === 'ACCESS_DENIED'
                ? '이 부서의 사원은 볼 수 있는 범위 밖입니다.'
                : getErrorMessage(err),
            );
          }
        }
      } catch (err) {
        toast.error(getErrorMessage(err));
      } finally {
        setDetailLoading(false);
      }
    },
    [canSeeEmployees],
  );

  useEffect(() => {
    loadTree()
      .then((t) => setSelectedId((prev) => prev ?? t.children[0]?.id ?? t.id))
      .catch((err) => setLoadError(getErrorMessage(err)));
    workScheduleApi
      .getDefault()
      .then(setDefaultSchedule)
      .catch(() => {});
  }, [loadTree]);

  useEffect(() => {
    loadDetail(selectedId);
  }, [selectedId, loadDetail]);

  const refresh = async (nextId = selectedId) => {
    await loadTree();
    if (nextId === selectedId) await loadDetail(nextId);
    else setSelectedId(nextId);
  };

  const toggle = (id) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const saveName = async () => {
    setSavingName(true);
    try {
      await departmentApi.update(detail.id, { name: name.trim() });
      toast.success('부서명을 바꿨습니다.');
      await refresh();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSavingName(false);
    }
  };

  const clearHead = async () => {
    const ok = await confirm({
      title: '부서장을 비울까요?',
      description: '비어 있는 동안 이 부서 사원의 결재는 상위 부서장에게 넘어갑니다.',
      confirmText: '비우기',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await departmentApi.clearHead(detail.id);
      toast.success('부서장을 비웠습니다.');
      await refresh();
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  const removeDepartment = async () => {
    const ok = await confirm({
      title: `'${detail.name}' 부서를 삭제할까요?`,
      description: '삭제한 부서는 되돌릴 수 없습니다.',
      confirmText: '삭제',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await departmentApi.remove(detail.id);
      toast.success('부서를 삭제했습니다.');
      await refresh(detail.parentId);
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  if (loadError) {
    return (
      <EmptyState icon={FolderTree} title="조직도를 불러오지 못했습니다" description={loadError} className="py-24" />
    );
  }
  if (!tree) return <PageLoader />;

  const isRoot = detail && detail.parentId === null;
  const notEmpty = detail && (detail.directMemberCount > 0 || detail.childCount > 0);

  return (
    <>
      <PageHeader
        title="부서 관리"
        description="조직도를 만들고 부서장과 근무시간을 정합니다. 부서장은 결재선에서 자동으로 결재자가 됩니다."
        actions={
          canCreate && (
            <Button icon={Plus} onClick={() => setModal('add')}>
              부서 추가
            </Button>
          )
        }
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <Card className="h-fit lg:col-span-4">
          <CardHeader title="조직도" description={`전체 ${tree.totalMemberCount}명`} />
          <div className="p-2">
            <ul role="tree" aria-label="조직도">
              <TreeNode
                node={tree}
                depth={0}
                selectedId={selectedId}
                collapsed={collapsed}
                onSelect={setSelectedId}
                onToggle={toggle}
              />
            </ul>
          </div>
        </Card>

        <div className="space-y-4 lg:col-span-8">
          {!detail ? (
            <Card>
              <PageLoader />
            </Card>
          ) : (
            <>
              <Card>
                <div className="flex flex-col gap-3 border-b border-gray-100 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate text-xs text-gray-500">
                      {detail.path.length ? detail.path.map((p) => p.name).join(' › ') : '최상위 부서'}
                    </p>
                    <h2 className="mt-0.5 flex items-center gap-2 text-lg font-semibold text-gray-900">
                      {detail.name}
                      {detailLoading && <Spinner size={14} />}
                    </h2>
                    <p className="mt-0.5 text-[13px] text-gray-500">
                      직속 {detail.directMemberCount}명 · 하위 부서 {detail.childCount}개
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-2">
                    {canCreate && (
                      <Button variant="secondary" size="sm" icon={Plus} onClick={() => setModal('add')}>
                        하위 부서
                      </Button>
                    )}
                    {canUpdate && !isRoot && (
                      <Button variant="secondary" size="sm" icon={Move} onClick={() => setModal('move')}>
                        옮기기
                      </Button>
                    )}
                    {canDelete && !isRoot && (
                      <Button
                        variant="danger-outline"
                        size="sm"
                        icon={Trash2}
                        onClick={removeDepartment}
                        disabled={notEmpty}
                        title={notEmpty ? '소속 사원이나 하위 부서가 있으면 삭제할 수 없습니다' : undefined}
                      >
                        삭제
                      </Button>
                    )}
                  </div>
                </div>

                <CardBody className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <Field label="부서명" htmlFor="dept-detail-name">
                    {canUpdate ? (
                      <div className="flex gap-2">
                        <Input id="dept-detail-name" value={name} onChange={(e) => setName(e.target.value)} />
                        <Button
                          variant="secondary"
                          onClick={saveName}
                          loading={savingName}
                          disabled={!name.trim() || name.trim() === detail.name}
                        >
                          저장
                        </Button>
                      </div>
                    ) : (
                      <p className="flex h-10 items-center text-sm text-gray-900">{detail.name}</p>
                    )}
                  </Field>
                  <Field label="부서 코드" htmlFor="dept-detail-code">
                    <Input id="dept-detail-code" value={detail.code} readOnly />
                  </Field>
                  <Field label="부서장" className="sm:col-span-2">
                    <div className="flex min-h-10 flex-wrap items-center gap-2.5">
                      {detail.head ? (
                        <>
                          <Avatar name={detail.head.name} size="sm" />
                          <span className="text-sm font-medium text-gray-900">{detail.head.name}</span>
                          <span className="text-[13px] text-gray-500">{detail.head.position}</span>
                        </>
                      ) : (
                        <span className="text-sm font-medium text-amber-700">지정되지 않음</span>
                      )}
                      {canUpdate && (
                        <span className="ml-auto flex gap-1.5">
                          {detail.head && (
                            <Button variant="ghost" size="sm" onClick={clearHead}>
                              비우기
                            </Button>
                          )}
                          <Button
                            variant={detail.head ? 'secondary' : 'primary'}
                            size="sm"
                            onClick={() => setModal('head')}
                            disabled={!canSeeEmployees}
                          >
                            {detail.head ? '변경' : '지정'}
                          </Button>
                        </span>
                      )}
                    </div>
                  </Field>
                </CardBody>

                {!detail.head && (
                  <div className="mx-5 mb-5 flex gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-3 text-[13px] text-amber-900">
                    <AlertTriangle size={16} className="mt-px shrink-0 text-amber-600" />
                    {detail.fallbackHead ? (
                      <p>
                        부서장이 비어 있는 동안 이 부서 사원의 결재는{' '}
                        <b className="font-semibold">
                          {detail.fallbackHead.departmentName} {detail.fallbackHead.name} {detail.fallbackHead.position}
                        </b>
                        에게 넘어갑니다.
                      </p>
                    ) : (
                      // 상위 부서에도 부서장이 없으면 결재선을 만들 수 없어 상신이 막힙니다(M2 명세 1.3).
                      <p>
                        상위 부서에도 부서장이 없어 이 부서 사원은 결재를 올릴 수 없습니다. 이 부서나 상위 부서의
                        부서장을 지정해 주세요.
                      </p>
                    )}
                  </div>
                )}
              </Card>

              <WorkScheduleCard
                key={`ws-${detail.id}-${JSON.stringify(detail.workSchedule)}`}
                department={detail}
                defaultSchedule={defaultSchedule}
                canUpdate={canUpdate}
                onSaved={() => refresh()}
              />

              {canSeeEmployees && (
                <Card className="overflow-hidden">
                  <CardHeader
                    title={`소속 사원 ${members.length}`}
                    description="이 부서에 직접 속한 사원입니다. 하위 부서 사원은 그 부서를 눌러 확인하세요."
                    action={
                      <Button
                        variant="ghost"
                        size="sm"
                        icon={Users}
                        onClick={() => navigate(`/org/employees?departmentId=${detail.id}`)}
                      >
                        사원 관리에서 보기
                      </Button>
                    }
                  />
                  <Table>
                    <thead>
                      <tr>
                        <Th>이름</Th>
                        <Th>아이디</Th>
                        <Th>직급</Th>
                        <Th>역할</Th>
                        <Th align="center">상태</Th>
                      </tr>
                    </thead>
                    <tbody>
                      {membersError ? (
                        <TableEmpty colSpan={5}>{membersError}</TableEmpty>
                      ) : members.length === 0 ? (
                        <TableEmpty colSpan={5}>이 부서에 직접 속한 사원이 없습니다.</TableEmpty>
                      ) : (
                        members.map((m) => (
                          <Tr key={m.id}>
                            <Td>
                              <span className="flex items-center gap-2.5">
                                <Avatar name={m.name} size="sm" />
                                <span className="font-medium text-gray-900">{m.name}</span>
                                {detail.head?.id === m.id && <Badge tone="blue">부서장</Badge>}
                              </span>
                            </Td>
                            <Td className="tabular-nums text-gray-500">{m.loginId}</Td>
                            <Td className="text-gray-600">{m.position}</Td>
                            <Td>
                              <span className="flex flex-wrap gap-1">
                                {m.roles.map((r) => (
                                  <Badge
                                    key={r.code}
                                    tone={r.code === 'CHIEF' ? 'red' : r.code === 'LEADER' ? 'blue' : 'gray'}
                                  >
                                    {r.name}
                                  </Badge>
                                ))}
                              </span>
                            </Td>
                            <Td align="center">
                              <Badge tone={USER_STATUS_META[m.status].tone} dot>
                                {USER_STATUS_META[m.status].label}
                              </Badge>
                            </Td>
                          </Tr>
                        ))
                      )}
                    </tbody>
                  </Table>
                </Card>
              )}
            </>
          )}
        </div>
      </div>

      <DepartmentFormModal
        open={modal === 'add'}
        onClose={() => setModal(null)}
        tree={tree}
        defaultParentId={detail?.id ?? tree.id}
        onCreated={async (id) => {
          setModal(null);
          await refresh(id);
        }}
      />
      <DepartmentMoveModal
        open={modal === 'move'}
        onClose={() => setModal(null)}
        tree={tree}
        department={detail}
        onMoved={async () => {
          setModal(null);
          await refresh();
        }}
      />
      <DepartmentHeadModal
        open={modal === 'head'}
        onClose={() => setModal(null)}
        department={detail}
        members={members}
        onSaved={async () => {
          setModal(null);
          await refresh();
        }}
      />
    </>
  );
}
