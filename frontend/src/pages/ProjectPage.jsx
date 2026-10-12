import React, { useState, useEffect } from 'react';
import { PRIORITY_META, TASK_STATUS_META } from '@/lib/labels';
import {
  Badge,
  Card,
  CardHeader,
  CardBody,
  PageHeader,
  PageLoader,
  Progress,
  Select,
  Table,
  Td,
  Th,
  Tr,
} from '@/components/ui';

const TASK_STATES = ['대기', '진행중', '완료'];

const ProjectPage = () => {
  const [projectProgress, setProjectProgress] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);

  // [2026-03-28] 설계서 v2.0 기준 변수명 적용
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        // 1. 프로젝트 진행도 가져오기 (4.2.10 API 매핑)
        // const resProgress = await axios.get('/projects/101/progress');
        // setProjectProgress(resProgress.data.data); // 설계서의 공통 응답 구조 적용

        // 2. 내 업무 목록 가져오기 (4.2.4 API 매핑)
        // const resTasks = await axios.get('/tasks?assignee=me');
        // setTasks(resTasks.data.data.content); // 페이지네이션 content 추출

        // 현재는 테스트용 가짜 데이터 (설계서와 필드명 일치시킴)
        setTimeout(() => {
          setProjectProgress({
            title: '사내 그룹웨어 개발', // 4.2.1
            progressRate: 65,            // 4.2.10
            doneTasks: 13,               // 4.2.10
            totalTasks: 20               // 4.2.10
          });
          setTasks([
            { id: 1, taskTitle: '로그인 API', state: '완료', priority: '높음', endDate: '2025.03.10' },
            { id: 2, taskTitle: '사용자 대시보드 UI 개발', state: '진행중', priority: '높음', endDate: '2025.03.26' },
            { id: 3, taskTitle: '연차 결재 로직 검토', state: '대기', priority: '중간', endDate: '2025.03.31' },
            { id: 4, taskTitle: '단위 테스트 코드 작성', state: '대기', priority: '낮음', endDate: '2025.04.10' },
            { id: 5, taskTitle: '배포 환경 설정', state: '대기', priority: '낮음', endDate: '2025.04.25' },
          ]);
          setLoading(false);
        }, 500);
      } catch (error) {
        console.error('데이터 로드 실패', error);
      }
    };
    fetchData();
  }, []);

  if (loading) return <PageLoader />;

  return (
    <>
      <PageHeader title="내 업무" description="참여 중인 프로젝트와 나에게 배정된 업무입니다." />

      {/* 프로젝트 요약 */}
      <Card>
        <CardBody>
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs text-gray-500">참여 프로젝트</p>
              <h2 className="mt-1 text-lg font-semibold text-gray-900">{projectProgress?.title}</h2>
            </div>
            <Badge tone="blue" dot>진행중</Badge>
          </div>
          <div className="mt-5">
            <div className="mb-2 flex items-baseline justify-between text-[13px]">
              <span className="text-gray-500">
                완료 {projectProgress?.doneTasks} / 전체 {projectProgress?.totalTasks}건
              </span>
              <span className="text-base font-semibold tabular-nums text-gray-900">{projectProgress?.progressRate}%</span>
            </div>
            <Progress value={projectProgress?.progressRate} className="h-2" />
          </div>
        </CardBody>
      </Card>

      {/* 업무 목록 */}
      <Card className="mt-4 overflow-hidden">
        <CardHeader title="내 업무 목록" description={`총 ${tasks.length}건`} />
        <Table>
          <thead>
            <tr>
              <Th>업무명</Th>
              <Th align="center">상태</Th>
              <Th align="center">우선순위</Th>
              <Th>마감일</Th>
              <Th align="right">상태 변경</Th>
            </tr>
          </thead>
          <tbody>
            {tasks.map(task => (
              <Tr key={task.id}>
                <Td className="font-medium text-gray-900">{task.taskTitle}</Td>
                <Td align="center"><Badge tone={TASK_STATUS_META[task.state]?.tone}>{task.state}</Badge></Td>
                <Td align="center"><Badge tone={PRIORITY_META[task.priority]?.tone}>{task.priority}</Badge></Td>
                <Td className="tabular-nums text-gray-500">{task.endDate}</Td>
                <Td align="right">
                  <div className="ml-auto w-28">
                    <Select defaultValue={task.state} className="h-8 text-[13px]" aria-label={`${task.taskTitle} 상태 변경`}>
                      {TASK_STATES.map((s) => <option key={s} value={s}>{s}</option>)}
                    </Select>
                  </div>
                </Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      </Card>
    </>
  );
};

export default ProjectPage;
