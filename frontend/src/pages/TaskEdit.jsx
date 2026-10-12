import React, { useState, useEffect } from 'react';
import { History } from 'lucide-react';
import { toast } from '@/lib/toast';
import {
  Button,
  Card,
  CardHeader,
  CardBody,
  CardFooter,
  EmptyState,
  Field,
  Input,
  PageHeader,
  Select,
  Textarea,
} from '@/components/ui';

// 가상 데이터 (API 연동 전)
const users = [
  { id: 'u1', name: '김철수 팀장', role: 'LEADER', projects: [1, 2] },
  { id: 'u2', name: '이영희 팀장', role: 'LEADER', projects: [3] },
  { id: 'u3', name: '박민준 사원', role: 'USER', projects: [1, 2] },
  { id: 'u4', name: '정수진 대리', role: 'USER', projects: [1, 3] },
  { id: 'u5', name: '최동현 주임', role: 'USER', projects: [2, 3] },
];

const projectData = [
  { id: 1, name: '사내 그룹웨어 개발', leaderId: 'u1' },
  { id: 2, name: 'ERP 시스템 고도화', leaderId: 'u1' },
  { id: 3, name: '모바일 앱 리뉴얼', leaderId: 'u2' },
];

const TaskEdit = () => {
  // 상태 관리
  const [formData, setFormData] = useState({
    projectId: '',
    title: '',
    teamLeaderName: '',
    memberId: '',
    priority: '중간',
    dueDate: '',
    description: '' // 업무 상세 내역
  });

  const [availableMembers, setAvailableMembers] = useState([]);

  // 프로젝트 선택 시 팀장/팀원 목록 갱신
  useEffect(() => {
    if (formData.projectId) {
      const pId = parseInt(formData.projectId);
      const selectedProject = projectData.find(p => p.id === pId);

      if (selectedProject) {
        // 1. 해당 프로젝트 팀장 찾기
        const leader = users.find(u => u.id === selectedProject.leaderId);

        // 2. 해당 프로젝트 참여 팀원 필터링
        const members = users.filter(u => u.projects.includes(pId) && u.role === 'USER');

        setAvailableMembers(members);

        // 프로젝트 선택 시 팀장 이름과 프로젝트 제목을 함께 세팅
        setFormData(prev => ({
          ...prev,
          teamLeaderName: leader ? leader.name : '지정된 팀장 없음',
          title: selectedProject.name, // 프로젝트 제목을 업무 제목 칸에 자동 입력
          memberId: '' // 팀원 선택은 초기화
        }));
      }
    } else {
      // 프로젝트 선택 해제 시 데이터 초기화
      setAvailableMembers([]);
      setFormData(prev => ({ ...prev, teamLeaderName: '', title: '', memberId: '' }));
    }
  }, [formData.projectId]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log('최종 업무 배정 데이터:', formData);
    toast.success('팀원에게 업무를 배정했습니다.');
  };

  return (
    <>
      <PageHeader title="업무 배정" description="프로젝트 팀원에게 업무를 배정하고 전달 사항을 작성합니다." />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        {/* 배정 폼 */}
        <Card className="h-fit lg:col-span-5">
          <CardHeader title="업무 정보" />
          <form onSubmit={handleSubmit}>
            <CardBody className="space-y-4">
              <Field label="프로젝트" htmlFor="task-project" required>
                <Select id="task-project" name="projectId" value={formData.projectId} onChange={handleChange} required>
                  <option value="">프로젝트를 선택하세요</option>
                  {projectData.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                </Select>
              </Field>

              <div className="grid grid-cols-2 gap-3">
                <Field label="담당 팀장" htmlFor="task-leader">
                  <Input id="task-leader" value={formData.teamLeaderName} placeholder="자동 입력" readOnly />
                </Field>
                <Field label="팀원" htmlFor="task-member" required>
                  <Select
                    id="task-member"
                    name="memberId"
                    value={formData.memberId}
                    onChange={handleChange}
                    disabled={!formData.projectId}
                    required
                  >
                    <option value="">{formData.projectId ? '팀원 선택' : '프로젝트 먼저 선택'}</option>
                    {availableMembers.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
                  </Select>
                </Field>
              </div>

              <Field label="업무 제목" htmlFor="task-title" required>
                <Input id="task-title" name="title" value={formData.title} onChange={handleChange} placeholder="업무의 핵심 제목을 입력하세요" required />
              </Field>

              <Field label="전달 사항" htmlFor="task-desc">
                <Textarea
                  id="task-desc"
                  name="description"
                  value={formData.description}
                  onChange={handleChange}
                  rows={4}
                  placeholder="팀원에게 전달할 업무 가이드나 요청 사항을 입력하세요"
                />
              </Field>

              <div className="grid grid-cols-2 gap-3">
                <Field label="우선순위" htmlFor="task-priority">
                  <Select id="task-priority" name="priority" value={formData.priority} onChange={handleChange}>
                    <option value="낮음">낮음</option>
                    <option value="중간">중간</option>
                    <option value="높음">높음</option>
                  </Select>
                </Field>
                <Field label="마감일" htmlFor="task-due" required>
                  <Input id="task-due" type="date" name="dueDate" value={formData.dueDate} onChange={handleChange} required />
                </Field>
              </div>
            </CardBody>
            <CardFooter>
              <Button type="submit">업무 배정</Button>
            </CardFooter>
          </form>
        </Card>

        {/* 배정 이력 */}
        <Card className="lg:col-span-7">
          <CardHeader title="최근 배정 내역" />
          <EmptyState
            icon={History}
            title="아직 배정한 업무가 없습니다"
            description="업무를 배정하면 이곳에 기록이 표시됩니다."
            className="py-24"
          />
        </Card>
      </div>
    </>
  );
};

export default TaskEdit;
