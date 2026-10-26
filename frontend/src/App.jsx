import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { getRoleInfo } from './lib/roles';
import { MENU } from './lib/permissions';

// 레이아웃 및 공통 컴포넌트
import AppLayout from './components/layout/AppLayout';
import { LegacyRedirect, RequireAuth, RequireLegacyRole, RequirePermission } from './components/layout/guards';
import { Toaster, ConfirmDialog } from './components/ui';

// 로그인 전
import LoginPage from './pages/LoginPage';
import FindIdPage from './pages/FindIdPage';
import FindPwPage from './pages/FindPwPage';
import PasswordSetupPage from './pages/PasswordSetupPage';
import ChangePasswordPage from './pages/ChangePasswordPage';

// 기존 기능
import AdminDashboard from './pages/AdminDashboard';
import UserDashboard from './pages/UserDashboard';
import TaskCreate from './pages/TaskCreate';
import TaskEdit from './pages/TaskEdit';
import ProjectManagement from './pages/ProjectManagement';
import AdminProjectAuth from './pages/AdminProjectAuth';
import ProjectPage from './pages/ProjectPage';
import CalendarPage from './pages/CalendarPage';

// 조직·권한 (M1)
import DepartmentsPage from './pages/org/DepartmentsPage';
import EmployeesPage from './pages/org/EmployeesPage';
import RolesPage from './pages/org/RolesPage';
import HolidaysPage from './pages/org/HolidaysPage';

// 전자결재 · 연차 (M2)
import ApprovalInboxPage from './pages/approval/ApprovalInboxPage';
import ApprovalDocumentPage from './pages/approval/ApprovalDocumentPage';
import ApprovalDraftPage from './pages/approval/ApprovalDraftPage';
import ApprovalFormsPage from './pages/approval/ApprovalFormsPage';
import MyLeavePage from './pages/leave/MyLeavePage';

// 근태·연차 관리 (M3)
import MyAttendancePage from './pages/attendance/MyAttendancePage';
import AttendanceMonthlyPage from './pages/attendance/AttendanceMonthlyPage';
import LeaveAdminPage from './pages/leave/LeaveAdminPage';

function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const { isChief, isTeamLeader } = getRoleInfo(user);
  return isChief || isTeamLeader ? <AdminDashboard /> : <UserDashboard />;
}

function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return (
    <BrowserRouter>
      <Routes>
        {/* 로그인 후: 경로는 역할과 상관없이 하나, 화면마다 권한 확인 */}
        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />

          <Route path="/approvals" element={<RequirePermission menu={MENU.APPROVAL_INBOX}><ApprovalInboxPage /></RequirePermission>} />
          <Route path="/approvals/new" element={<RequirePermission menu={MENU.APPROVAL_DRAFT} action="C"><ApprovalDraftPage /></RequirePermission>} />
          <Route path="/approvals/:id" element={<ApprovalDocumentPage />} />
          <Route path="/approval-forms" element={<RequirePermission menu={MENU.APPROVAL_FORM}><ApprovalFormsPage /></RequirePermission>} />
          <Route path="/leaves" element={<MyLeavePage />} />
          <Route path="/attendance" element={<RequirePermission menu={MENU.MY_ATTENDANCE}><MyAttendancePage /></RequirePermission>} />
          <Route path="/attendance/monthly" element={<RequirePermission menu={MENU.ATTENDANCE_MONTHLY}><AttendanceMonthlyPage /></RequirePermission>} />
          <Route path="/leave/admin" element={<RequirePermission menu={MENU.LEAVE_ADMIN}><LeaveAdminPage /></RequirePermission>} />
          <Route path="/leaves/new" element={<Navigate to="/approvals/new?form=LEAVE" replace />} />
          <Route path="/calendar" element={<CalendarPage />} />

          <Route path="/projects" element={<RequireLegacyRole><ProjectManagement /></RequireLegacyRole>} />
          <Route path="/projects/new" element={<RequireLegacyRole><TaskCreate /></RequireLegacyRole>} />
          <Route path="/tasks/assign" element={<RequireLegacyRole><TaskEdit /></RequireLegacyRole>} />
          <Route path="/my-tasks" element={<ProjectPage />} />
          <Route path="/project-auth" element={<RequireLegacyRole chiefOnly><AdminProjectAuth /></RequireLegacyRole>} />

          <Route path="/holidays" element={<RequirePermission menu={MENU.HOLIDAY}><HolidaysPage /></RequirePermission>} />
          <Route path="/org/departments" element={<RequirePermission menu={MENU.DEPARTMENT}><DepartmentsPage /></RequirePermission>} />
          <Route path="/org/employees" element={<RequirePermission menu={MENU.EMPLOYEE}><EmployeesPage /></RequirePermission>} />
          <Route path="/org/roles" element={<RequirePermission menu={MENU.ROLE}><RolesPage /></RequirePermission>} />
        </Route>

        {/* 로그인 전 · 비밀번호 */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/find-id" element={<FindIdPage />} />
        <Route path="/find-pw" element={<FindPwPage />} />
        <Route path="/password/setup" element={<PasswordSetupPage />} />
        <Route path="/me/password" element={<ChangePasswordPage />} />
        {/* 회원가입은 없습니다. 사원은 관리자가 등록합니다. */}
        <Route path="/signup" element={<Navigate to="/login" replace />} />

        {/* 예전 역할별 경로 호환 */}
        <Route path="/admin/*" element={<LegacyRedirect area="admin" />} />
        <Route path="/manager/*" element={<LegacyRedirect area="manager" />} />
        <Route path="/user/*" element={<LegacyRedirect area="user" />} />
        <Route path="/project-page" element={<Navigate to="/my-tasks" replace />} />

        <Route path="/" element={<Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      <Toaster />
      <ConfirmDialog />
    </BrowserRouter>
  );
}

export default App;
