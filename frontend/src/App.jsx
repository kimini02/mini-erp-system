import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { getRoleInfo } from './lib/roles';

// 레이아웃 및 공통 컴포넌트
import AppLayout from './components/layout/AppLayout';
import { Toaster, ConfirmDialog } from './components/ui';

// 페이지
import AdminDashboard from './pages/AdminDashboard';
import UserDashboard from './pages/UserDashboard';
import LoginPage from './pages/LoginPage';
import TaskCreate from './pages/TaskCreate';
import TaskEdit from './pages/TaskEdit';
import ProjectManagement from './pages/ProjectManagement';
import AdminProjectAuth from './pages/AdminProjectAuth';
import ProjectPage from './pages/ProjectPage';
import CalendarPage from './pages/CalendarPage';
import SignupPage from './pages/SignupPage';
import FindIdPage from './pages/FindIdPage';
import FindPwPage from './pages/FindPwPage';

// 연차 관련 페이지
import LeaveHistoryPage from './pages/LeaveHistoryPage';
import LeaveApplyPage from './pages/LeaveApplyPage';
import LeaveApprovalPage from './pages/LeaveApprovalPage';

/**
 * 권한 보호 컴포넌트 (ProtectedRoute)
 * 여러 권한을 허용할 수 있도록 allowedRoles 배열을 체크합니다.
 */
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { isAuthenticated, user } = useAuthStore();

  // 1. 로그인 여부 확인
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // 2. 권한 확인: TEAM_LEADER와 MANAGER 권한을 동일하게 취급
  const userRole = user?.role;
  const hasAccess = allowedRoles.includes(userRole) ||
                   (allowedRoles.includes('MANAGER') && userRole === 'TEAM_LEADER');

  if (allowedRoles && !hasAccess) {
    return <Navigate to={getRoleInfo(user).homePath} replace />;
  }

  return children;
};

function App() {
  const { isAuthenticated, user } = useAuthStore();

  return (
    <BrowserRouter>
      <Routes>
        {/* ============================================================
           1. 관리소장 영역 (/admin) - 팀장도 접근 가능, 화면 내부에서 권한 분기
        ============================================================ */}
        <Route
          path="/admin"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'TEAM_LEADER', 'MANAGER']}>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboard />} />
          <Route path="project-auth" element={<AdminProjectAuth />} />
          <Route path="task-create" element={<TaskCreate />} />
          <Route path="task-edit" element={<TaskEdit />} />
          <Route path="projects" element={<ProjectManagement />} />
          <Route path="approvals" element={<LeaveApprovalPage />} />
          <Route path="leave-approval" element={<LeaveApprovalPage />} />
        </Route>

        {/* ============================================================
           2. 팀장 영역 (/manager)
        ============================================================ */}
        <Route
          path="/manager"
          element={
            <ProtectedRoute allowedRoles={['TEAM_LEADER', 'MANAGER']}>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboard />} />
          <Route path="approvals" element={<LeaveApprovalPage />} />
          <Route path="leave-approval" element={<LeaveApprovalPage />} />
          <Route path="leaves" element={<LeaveHistoryPage />} />
          <Route path="leaves/new" element={<LeaveApplyPage />} />
          <Route path="task-create" element={<TaskCreate />} />
          <Route path="task-edit" element={<TaskEdit />} />
          <Route path="projects" element={<ProjectManagement />} />
        </Route>

        {/* ============================================================
           3. 일반 사원 영역 (/user)
        ============================================================ */}
        <Route
          path="/user"
          element={
            <ProtectedRoute allowedRoles={['USER']}>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<UserDashboard />} />
          <Route path="projects" element={<ProjectPage />} />
          <Route path="calendar" element={<CalendarPage />} />
          <Route path="leaves" element={<LeaveHistoryPage />} />
          <Route path="leaves/new" element={<LeaveApplyPage />} />
        </Route>

        {/* 이전 사원 경로 호환 */}
        <Route path="/project-page" element={<Navigate to="/user/projects" replace />} />
        <Route path="/leaves" element={<Navigate to="/user/leaves" replace />} />
        <Route path="/leaves/new" element={<Navigate to="/user/leaves/new" replace />} />

        {/* ============================================================
           4. 공통 영역
        ============================================================ */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/find-id" element={<FindIdPage />} />
        <Route path="/find-pw" element={<FindPwPage />} />

        {/* 루트 경로: 권한별 홈으로 이동 */}
        <Route
          path="/"
          element={
            isAuthenticated
              ? <Navigate to={getRoleInfo(user).homePath} replace />
              : <Navigate to="/login" replace />
          }
        />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      <Toaster />
      <ConfirmDialog />
    </BrowserRouter>
  );
}

export default App;
