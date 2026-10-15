// 개발용 가짜 데이터 (실제 사람·회사 아님). 화면 시안과 같은 조직입니다.
// 가짜 서버의 모든 계정 비밀번호: Mock1234!  (firstadmin 은 첫 로그인 때 비밀번호 변경 강제)

export const MOCK_PASSWORD = 'Mock1234!';

export const menus = [
  { code: 'APPROVAL_INBOX', name: '결재함', group: '전자결재', allowedActions: ['V'], scopeApplicable: false },
  { code: 'APPROVAL_DRAFT', name: '기안하기', group: '전자결재', allowedActions: ['C'], scopeApplicable: false },
  { code: 'APPROVAL_FORM', name: '결재 양식 관리', group: '전자결재', allowedActions: ['V', 'U'], scopeApplicable: false },
  { code: 'MY_ATTENDANCE', name: '내 근태', group: '근태·휴가', allowedActions: ['V', 'C'], scopeApplicable: false },
  { code: 'ATTENDANCE_MONTHLY', name: '월간 근태표', group: '근태·휴가', allowedActions: ['V', 'U'], scopeApplicable: true },
  { code: 'LEAVE_ADMIN', name: '연차 관리', group: '근태·휴가', allowedActions: ['V', 'C', 'U'], scopeApplicable: true },
  { code: 'DEPARTMENT', name: '부서 관리', group: '조직', allowedActions: ['V', 'C', 'U', 'D'], scopeApplicable: false },
  { code: 'EMPLOYEE', name: '사원 관리', group: '조직', allowedActions: ['V', 'C', 'U', 'D'], scopeApplicable: true },
  { code: 'ROLE', name: '역할·권한', group: '조직', allowedActions: ['V', 'C', 'U', 'D'], scopeApplicable: false },
  { code: 'PROJECT', name: '프로젝트', group: '업무·시스템', allowedActions: ['V', 'C', 'U', 'D'], scopeApplicable: true },
  { code: 'HOLIDAY', name: '공휴일', group: '업무·시스템', allowedActions: ['V', 'C', 'U', 'D'], scopeApplicable: false },
];

const p = (actions, scope = null) => ({
  view: actions.includes('V'),
  create: actions.includes('C'),
  update: actions.includes('U'),
  delete: actions.includes('D'),
  scope,
});

export function createDb() {
  const roles = [
    { id: 1, code: 'CHIEF', name: '관리소장', description: '회사 전체를 관리합니다. 최초 설치 때 만들어지는 계정이 가집니다.', system: true, locked: true },
    { id: 2, code: 'LEADER', name: '팀장', description: '부서원의 결재를 처리하고 소속 부서의 근태와 연차를 봅니다.', system: true, locked: false },
    { id: 3, code: 'EMPLOYEE', name: '사원', description: '모든 사원이 기본으로 가집니다. 본인 데이터만 봅니다.', system: true, locked: false },
    { id: 4, code: 'CUSTOM_4', name: '인사 담당', description: '사원 등록과 연차 조정을 맡습니다. 휴가 결재에 자동으로 참조됩니다.', system: false, locked: false },
    { id: 5, code: 'CUSTOM_5', name: '근태 담당', description: '월간 근태표를 관리하고 공휴일을 등록합니다.', system: false, locked: false },
    { id: 6, code: 'CUSTOM_6', name: '재무 승인', description: "일반 품의 결재선의 '재무 승인' 단계에 자동으로 들어갑니다.", system: false, locked: false },
  ];

  const allMenus = Object.fromEntries(
    menus.map((m) => [m.code, p(m.allowedActions, m.scopeApplicable ? 'ALL' : null)])
  );

  const rolePermissions = {
    CHIEF: allMenus,
    LEADER: {
      APPROVAL_INBOX: p(['V']),
      APPROVAL_DRAFT: p(['C']),
      MY_ATTENDANCE: p(['V', 'C']),
      ATTENDANCE_MONTHLY: p(['V'], 'DEPT_TREE'),
      LEAVE_ADMIN: p(['V'], 'DEPT_TREE'),
      DEPARTMENT: p(['V']),
      EMPLOYEE: p(['V'], 'DEPT_TREE'),
      PROJECT: p(['V', 'C', 'U'], 'DEPT'),
      HOLIDAY: p(['V']),
    },
    EMPLOYEE: {
      APPROVAL_INBOX: p(['V']),
      APPROVAL_DRAFT: p(['C']),
      MY_ATTENDANCE: p(['V', 'C']),
      PROJECT: p(['V'], 'SELF'),
      HOLIDAY: p(['V']),
    },
    CUSTOM_4: {
      LEAVE_ADMIN: p(['V', 'C', 'U'], 'ALL'),
      DEPARTMENT: p(['V']),
      EMPLOYEE: p(['V', 'C', 'U'], 'ALL'),
      ATTENDANCE_MONTHLY: p(['V'], 'ALL'),
    },
    CUSTOM_5: {
      ATTENDANCE_MONTHLY: p(['V', 'U'], 'ALL'),
      HOLIDAY: p(['V', 'C', 'U', 'D']),
    },
    CUSTOM_6: {
      APPROVAL_INBOX: p(['V']),
    },
  };

  const departments = [
    { id: 1, name: 'WorkFlow 주식회사', code: 'D-0001', parentId: null, headUserId: 1, sortOrder: 1, schedule: null },
    { id: 2, name: '경영지원본부', code: 'D-0002', parentId: 1, headUserId: 1, sortOrder: 1, schedule: null },
    { id: 3, name: '인사팀', code: 'D-0003', parentId: 2, headUserId: 2, sortOrder: 1, schedule: null },
    { id: 4, name: '재무팀', code: 'D-0004', parentId: 2, headUserId: 5, sortOrder: 2, schedule: null },
    { id: 5, name: '개발본부', code: 'D-0005', parentId: 1, headUserId: 7, sortOrder: 2, schedule: null },
    { id: 6, name: '개발1팀', code: 'D-0006', parentId: 5, headUserId: 8, sortOrder: 1, schedule: null },
    { id: 7, name: 'QA팀', code: 'D-0007', parentId: 5, headUserId: 18, sortOrder: 3, schedule: null },
    { id: 8, name: '개발2팀', code: 'D-0008', parentId: 5, headUserId: null, sortOrder: 2, schedule: { startTime: '10:00', endTime: '19:00', lateGraceMin: 10 } },
    { id: 9, name: '영업본부', code: 'D-0009', parentId: 1, headUserId: 20, sortOrder: 3, schedule: null },
    { id: 10, name: '국내영업팀', code: 'D-0010', parentId: 9, headUserId: 21, sortOrder: 1, schedule: null },
  ];

  const positions = [
    { id: 1, name: '사원', rankOrder: 1 },
    { id: 2, name: '주임', rankOrder: 2 },
    { id: 3, name: '대리', rankOrder: 3 },
    { id: 4, name: '과장', rankOrder: 4 },
    { id: 5, name: '팀장', rankOrder: 5 },
    { id: 6, name: '본부장', rankOrder: 6 },
    { id: 7, name: '관리소장', rankOrder: 7 },
  ];

  const u = (id, loginId, name, email, departmentId, positionId, roles, hireDate, extra = {}) => ({
    id, loginId, name, email, departmentId, positionId, roles, hireDate,
    status: 'ACTIVE', invitationPending: false, mustChangePw: false, tokenVersion: 0,
    password: MOCK_PASSWORD, ...extra,
  });

  const users = [
    u(1, '2015001', '윤재원', 'jw.yoon@workflow.test', 2, 7, ['CHIEF'], '2015-03-02'),
    u(2, '2017002', '문가영', 'gy.moon@workflow.test', 3, 5, ['LEADER'], '2017-07-03'),
    u(3, '2021001', '이수아', 'sa.lee@workflow.test', 3, 3, ['EMPLOYEE', 'CUSTOM_4'], '2021-01-04'),
    u(4, '2024003', '조은비', 'eb.cho@workflow.test', 3, 1, ['EMPLOYEE', 'CUSTOM_5'], '2024-03-04'),
    u(5, '2016005', '오세린', 'sr.oh@workflow.test', 4, 5, ['LEADER', 'CUSTOM_6'], '2016-09-01'),
    u(6, '2019003', '남기훈', 'kh.nam@workflow.test', 4, 4, ['EMPLOYEE'], '2019-02-11'),
    u(7, '2014002', '최민수', 'ms.choi@workflow.test', 5, 6, ['LEADER'], '2014-05-12'),
    u(8, '2018004', '박지훈', 'jh.park@workflow.test', 6, 5, ['LEADER'], '2018-04-02'),
    u(9, '2019011', '정하은', 'he.jung@workflow.test', 6, 4, ['EMPLOYEE'], '2019-08-19'),
    u(10, '2021007', '이도윤', 'dy.lee@workflow.test', 6, 3, ['EMPLOYEE'], '2021-06-07'),
    u(11, '2022010', '오태민', 'tm.oh@workflow.test', 6, 2, ['EMPLOYEE'], '2022-10-04'),
    u(12, '2023002', '김서연', 'sy.kim@workflow.test', 6, 1, ['EMPLOYEE'], '2023-03-02'),
    u(13, '2024008', '한유진', 'yj.han@workflow.test', 6, 1, ['EMPLOYEE'], '2024-07-01'),
    u(14, '2026003', '신동현', 'dh.shin@workflow.test', 6, 1, ['EMPLOYEE'], '2026-10-05', { invitationPending: true, password: null, invitationVersion: 1 }),
    u(15, '2023009', '서지우', 'jw.seo@workflow.test', 8, 1, ['EMPLOYEE'], '2023-09-04'),
    u(16, '2020004', '권태양', 'ty.kwon@workflow.test', 8, 3, ['EMPLOYEE'], '2020-11-02'),
    u(17, '2022012', '유나래', 'nr.yu@workflow.test', 8, 2, ['EMPLOYEE'], '2022-04-18', { status: 'ON_LEAVE' }),
    u(18, '2017001', '한소희', 'sh.han@workflow.test', 7, 5, ['LEADER'], '2017-01-09'),
    u(19, '2025001', '민경준', 'kj.min@workflow.test', 7, 1, ['EMPLOYEE'], '2025-01-06'),
    u(20, '2013001', '배성호', 'sh.bae@workflow.test', 9, 6, ['LEADER'], '2013-11-04'),
    u(21, '2018002', '임채원', 'cw.lim@workflow.test', 10, 5, ['LEADER'], '2018-02-05'),
    u(22, '2021003', '고민재', 'mj.ko@workflow.test', 10, 3, ['EMPLOYEE'], '2021-03-15'),
    u(25, 'user01', '장민혁', 'mh.jang@workflow.test', 6, 3, ['EMPLOYEE'], null),
    u(23, '2020015', '권민지', 'mj.kwon@workflow.test', 7, 3, ['EMPLOYEE'], '2020-03-09', { status: 'RETIRED' }),
    u(24, 'firstadmin', '최초 관리자', 'admin@workflow.test', 1, 7, ['CHIEF'], '2026-10-01', { mustChangePw: true }),
  ];

  const holidays = [
    ['2026-01-01', '신정'], ['2026-02-16', '설날 연휴'], ['2026-02-17', '설날'], ['2026-02-18', '설날 연휴'],
    ['2026-03-02', '대체공휴일(삼일절)', true], ['2026-05-05', '어린이날'], ['2026-05-25', '대체공휴일(부처님오신날)', true],
    ['2026-06-03', '전국동시지방선거'], ['2026-08-17', '대체공휴일(광복절)', true], ['2026-09-24', '추석 연휴'],
    ['2026-09-25', '추석'], ['2026-09-26', '추석 연휴'], ['2026-10-05', '대체공휴일(개천절)', true],
    ['2026-10-09', '한글날'], ['2026-12-25', '성탄절'],
  ].map(([date, name, substitute = false], i) => ({ id: i + 1, date, name, substitute }));

  return {
    roles,
    rolePermissions,
    departments,
    positions,
    users,
    holidays,
    defaultSchedule: { startTime: '09:00', endTime: '18:00', lateGraceMin: 10 },
    setupTokens: { 'invite-14': 14 },
    seq: { role: 7, department: 11, user: 26, holiday: 100 },
    mailEnabled: true,
  };
}
