import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from '@/lib/toast';
import { Button, Card, CardBody, CardFooter, Field, Input, PageHeader, Segmented, Select, Textarea } from '@/components/ui';

// 사용자 데이터 (실제로는 API에서 가져오게 됩니다)
const users = [
  { id: 'u1', name: '김철수 팀장', role: 'LEADER' },
  { id: 'u2', name: '이영희 팀장', role: 'LEADER' },
  { id: 'u3', name: '박민준 사원', role: 'USER' },
  { id: 'u4', name: '정수진 대리', role: 'USER' },
];

// 팀장급(LEADER)만 필터링
const leaders = users.filter(user => user.role === 'LEADER');

const INITIAL_FORM = {
  leaderId: '',     // 담당 팀장 ID
  title: '',        // 프로젝트 제목
  startDate: '',    // 시작일
  endDate: '',      // 종료일(마감)
  priority: '중간', // 우선순위
  description: ''   // 설명
};

const TaskCreate = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState(INITIAL_FORM);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log('새 Project 생성 데이터:', formData);
    toast.success(`'${formData.title}' 프로젝트가 생성되어 팀장에게 배정되었습니다.`);
    setFormData(INITIAL_FORM);
  };

  return (
    <>
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="mb-3 inline-flex items-center gap-1 text-[13px] text-gray-500 hover:text-gray-900"
      >
        <ArrowLeft size={14} /> 프로젝트 관리
      </button>
      <PageHeader title="새 프로젝트" description="프로젝트를 만들고 담당 팀장에게 배정합니다." />

      <Card className="max-w-3xl">
        <form onSubmit={handleSubmit}>
          <CardBody className="space-y-5">
            <Field label="프로젝트명" htmlFor="pj-title" required>
              <Input id="pj-title" name="title" value={formData.title} onChange={handleChange} placeholder="예: 사내 그룹웨어 개발" required />
            </Field>

            <Field label="담당 팀장" htmlFor="pj-leader" required>
              <Select id="pj-leader" name="leaderId" value={formData.leaderId} onChange={handleChange} required>
                <option value="">팀장을 선택하세요</option>
                {leaders.map(leader => (
                  <option key={leader.id} value={leader.id}>{leader.name}</option>
                ))}
              </Select>
            </Field>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="시작일" htmlFor="pj-start" required>
                <Input id="pj-start" type="date" name="startDate" value={formData.startDate} onChange={handleChange} required />
              </Field>
              <Field label="종료일 (마감)" htmlFor="pj-end" required>
                <Input id="pj-end" type="date" name="endDate" value={formData.endDate} min={formData.startDate || undefined} onChange={handleChange} required />
              </Field>
            </div>

            <Field label="우선순위">
              <Segmented
                className="max-w-xs"
                options={['낮음', '중간', '높음']}
                value={formData.priority}
                onChange={(p) => setFormData(prev => ({ ...prev, priority: p }))}
              />
            </Field>

            <Field label="설명" htmlFor="pj-desc">
              <Textarea
                id="pj-desc"
                name="description"
                rows={5}
                value={formData.description}
                onChange={handleChange}
                placeholder="프로젝트 목표와 가이드라인을 입력하세요"
              />
            </Field>
          </CardBody>
          <CardFooter>
            <Button variant="secondary" onClick={() => navigate(-1)}>취소</Button>
            <Button type="submit">프로젝트 생성</Button>
          </CardFooter>
        </form>
      </Card>
    </>
  );
};

export default TaskCreate;
