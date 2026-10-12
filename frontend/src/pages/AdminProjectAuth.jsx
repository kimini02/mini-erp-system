import React, { useState, useEffect, useCallback } from 'react';
import { Search, Users, FolderLock } from 'lucide-react';
import axios from '../api/axios';
import { PROJECT_STATUS_LABELS } from '@/lib/labels';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Avatar,
  Badge,
  Button,
  Card,
  CardHeader,
  CardFooter,
  EmptyState,
  Input,
  PageHeader,
  PageLoader,
} from '@/components/ui';

/** 켜기/끄기 스위치 */
const Switch = ({ checked }) => (
  <span
    className={cn(
      'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors',
      checked ? 'bg-blue-600' : 'bg-gray-200'
    )}
  >
    <span
      className={cn(
        'inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform',
        checked ? 'translate-x-[18px]' : 'translate-x-0.5'
      )}
    />
  </span>
);

const AdminProjectAuth = () => {
  // --- 1. 상태 관리 ---
  const [users, setUsers] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedUser, setSelectedUser] = useState(null);
  const [projectList, setProjectList] = useState([]);
  const [loading, setLoading] = useState({ users: false, projects: false, saving: false });

  // --- 2. 데이터 페칭 로직 ---
  const fetchUsers = useCallback(async () => {
    setLoading(prev => ({ ...prev, users: true }));
    try {
      const response = await axios.get('/users?size=100');
      if (response.data.success) {
        setUsers(response.data.data.content);
      }
    } catch (error) {
      console.error('멤버 목록 로드 실패:', error);
    } finally {
      setLoading(prev => ({ ...prev, users: false }));
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const fetchUserPermissions = async (user) => {
    const targetId = user.id;
    setSelectedUser(user);
    setLoading(prev => ({ ...prev, projects: true }));

    try {
      const response = await axios.get(`/projects/permissions/${targetId}`);
      if (response.data.success) {
        setProjectList(response.data.data);
      }
    } catch (error) {
      console.error('권한 로드 실패:', error);
      toast.error('해당 사용자의 프로젝트 권한 정보를 가져오지 못했습니다.');
    } finally {
      setLoading(prev => ({ ...prev, projects: false }));
    }
  };

  // --- 3. 핸들러 ---
  const handleAuthToggle = (projectId) => {
    setProjectList(prev => prev.map(proj =>
      proj.projectId === projectId ? { ...proj, assigned: !proj.assigned } : proj
    ));
  };

  const handleSaveAuth = async () => {
    if (!selectedUser) return;
    setLoading(prev => ({ ...prev, saving: true }));

    const payload = {
      assignedProjectIds: projectList
        .filter(p => p.assigned)
        .map(p => p.projectId)
    };

    try {
      const response = await axios.put(`/projects/permissions/${selectedUser.id}`, payload);
      if (response.data.success) {
        toast.success(`${selectedUser.name}님의 권한을 저장했습니다.`);
        fetchUsers();
      }
    } catch (error) {
      console.error('저장 실패:', error);
      toast.error('저장 중 오류가 발생했습니다.');
    } finally {
      setLoading(prev => ({ ...prev, saving: false }));
    }
  };

  /**
   * 필터링: 관리자(ADMIN) 계정 제외 + 이름 검색
   */
  const filteredUsers = users.filter(user =>
    user.role !== 'ADMIN' && user.name.includes(searchTerm)
  );

  const assignedCount = projectList.filter(p => p.assigned).length;

  return (
    <>
      <PageHeader title="프로젝트 권한" description="관리자를 제외한 팀장·팀원의 프로젝트 접근 권한을 관리합니다." />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        {/* 사용자 목록 */}
        <Card className="flex flex-col overflow-hidden lg:col-span-5">
          <CardHeader title="멤버" description={`${filteredUsers.length}명`} />
          <div className="border-b border-gray-100 px-4 py-3">
            <Input
              icon={Search}
              placeholder="이름으로 검색"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="h-9"
            />
          </div>

          <div className="h-[480px] overflow-y-auto p-2">
            {loading.users ? (
              <PageLoader className="py-16" />
            ) : filteredUsers.length > 0 ? (
              <ul className="space-y-0.5">
                {filteredUsers.map(user => {
                  const active = selectedUser?.id === user.id;
                  return (
                    <li key={user.id}>
                      <button
                        type="button"
                        onClick={() => fetchUserPermissions(user)}
                        className={cn(
                          'flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors',
                          active ? 'bg-blue-50' : 'hover:bg-gray-50'
                        )}
                      >
                        <Avatar name={user.name} />
                        <div className="min-w-0 flex-1">
                          <p className={cn('text-sm font-medium', active ? 'text-blue-700' : 'text-gray-900')}>{user.name}</p>
                          <p className="truncate text-xs text-gray-500">
                            {[user.departmentName, user.position].filter(Boolean).join(' · ')}
                          </p>
                        </div>
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <EmptyState icon={Users} title="표시할 멤버가 없습니다" />
            )}
          </div>
        </Card>

        {/* 권한 설정 */}
        <Card className="flex flex-col overflow-hidden lg:col-span-7">
          <CardHeader
            title="프로젝트 접근 권한"
            description={selectedUser ? `${selectedUser.name}님의 권한을 편집 중입니다` : '왼쪽 목록에서 멤버를 선택하세요'}
            action={selectedUser && projectList.length > 0 && <Badge tone="blue">{assignedCount}개 허용</Badge>}
          />

          <div className="h-[480px] overflow-y-auto">
            {!selectedUser ? (
              <EmptyState icon={FolderLock} title="선택된 멤버가 없습니다" description="멤버를 선택하면 프로젝트별 권한을 설정할 수 있습니다." className="h-full" />
            ) : loading.projects ? (
              <PageLoader className="py-16" />
            ) : projectList.length > 0 ? (
              <ul className="divide-y divide-gray-100">
                {projectList.map(project => (
                  <li key={project.projectId}>
                    <button
                      type="button"
                      role="switch"
                      aria-checked={project.assigned}
                      onClick={() => handleAuthToggle(project.projectId)}
                      className="flex w-full items-center justify-between gap-4 px-5 py-3.5 text-left hover:bg-gray-50"
                    >
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-900">{project.title}</p>
                        <p className="mt-0.5 text-xs text-gray-500">상태: {PROJECT_STATUS_LABELS[project.status] || project.status}</p>
                      </div>
                      <div className="flex items-center gap-2.5">
                        <span className={cn('text-xs', project.assigned ? 'font-medium text-blue-600' : 'text-gray-400')}>
                          {project.assigned ? '허용' : '차단'}
                        </span>
                        <Switch checked={project.assigned} />
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState icon={FolderLock} title="등록된 프로젝트가 없습니다" className="h-full" />
            )}
          </div>

          <CardFooter>
            <Button onClick={handleSaveAuth} disabled={!selectedUser} loading={loading.saving}>
              변경 사항 저장
            </Button>
          </CardFooter>
        </Card>
      </div>
    </>
  );
};

export default AdminProjectAuth;
