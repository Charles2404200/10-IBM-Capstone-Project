import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import AppShell from '@/components/layout/AppShell'
import LoadingState from '@/components/shared/LoadingState'

const LandingPage = lazy(() => import('@/pages/Landing/LandingPage'))
const LoginPage = lazy(() => import('@/pages/Auth/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/Auth/RegisterPage'))
const VerifyEmailPage = lazy(() => import('@/pages/Auth/VerifyEmailPage'))
const ForgotPasswordPage = lazy(() => import('@/pages/Auth/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('@/pages/Auth/ResetPasswordPage'))
const CommandCentrePage = lazy(() => import('@/pages/CommandCentre/CommandCentrePage'))
const LeadPipelinePage = lazy(() => import('@/pages/LeadPipeline/LeadPipelinePage'))
const ClientIntelligencePage = lazy(() => import('@/pages/ClientIntelligence/ClientIntelligencePage'))
const OutreachWorkspacePage = lazy(() => import('@/pages/OutreachWorkspace/OutreachWorkspacePage'))
const MeetingPreparationPage = lazy(() => import('@/pages/MeetingPreparation/MeetingPreparationPage'))
const LiveMeetingPage = lazy(() => import('@/pages/LiveMeeting/LiveMeetingPage'))
const ProposalStudioPage = lazy(() => import('@/pages/ProposalStudio/ProposalStudioPage'))
const AssessmentReviewPage = lazy(() => import('@/pages/AssessmentReview/AssessmentReviewPage'))
const PortfolioPage = lazy(() => import('@/pages/Portfolio/PortfolioPage'))
const ScenarioBuilderPage = lazy(() => import('@/pages/Admin/ScenarioBuilderPage'))
const AchievementBuilderPage = lazy(() => import('@/pages/Admin/AchievementBuilderPage'))
const AdminConsolePage = lazy(() => import('@/pages/Admin/AdminConsolePage'))
const UserManagementPage = lazy(() => import('@/pages/Admin/UserManagementPage'))
const AiOperationsPage = lazy(() => import('@/pages/Admin/AiOperationsPage'))

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated())
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

/** Redirects already-authenticated visitors away from public marketing/auth pages. */
function PublicOnlyRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated())
  return isAuthenticated ? <Navigate to="/dashboard" replace /> : <>{children}</>
}

/** Gates admin-only sandbox routes (scenario/achievement builders) by role,
 *  redirecting non-privileged learners back to the Command Centre. */
function RequireRole({ roles, children }: { roles: string[]; children: React.ReactNode }) {
  const role = useAuthStore((s) => s.role)
  return role && roles.includes(role) ? <>{children}</> : <Navigate to="/dashboard" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<LoadingState />}>
        <Routes>
        <Route
          path="/"
          element={
            <PublicOnlyRoute>
              <LandingPage />
            </PublicOnlyRoute>
          }
        />
        <Route
          path="/login"
          element={
            <PublicOnlyRoute>
              <LoginPage />
            </PublicOnlyRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicOnlyRoute>
              <RegisterPage />
            </PublicOnlyRoute>
          }
        />
        <Route path="/verify-email" element={<PublicOnlyRoute><VerifyEmailPage /></PublicOnlyRoute>} />
        <Route path="/forgot-password" element={<PublicOnlyRoute><ForgotPasswordPage /></PublicOnlyRoute>} />
        <Route path="/reset-password" element={<PublicOnlyRoute><ResetPasswordPage /></PublicOnlyRoute>} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <AppShell />
            </ProtectedRoute>
          }
        >
          <Route index element={<CommandCentrePage />} />
          <Route path="engagements/:engagementId/leads" element={<LeadPipelinePage />} />
          <Route path="engagements/:engagementId/intelligence" element={<ClientIntelligencePage />} />
          <Route path="engagements/:engagementId/outreach" element={<OutreachWorkspacePage />} />
          <Route path="engagements/:engagementId/preparation" element={<MeetingPreparationPage />} />
          <Route path="engagements/:engagementId/meetings/:meetingId" element={<LiveMeetingPage />} />
          <Route path="engagements/:engagementId/proposal" element={<ProposalStudioPage />} />
          <Route path="engagements/:engagementId/assessment" element={<AssessmentReviewPage />} />
          <Route path="portfolio" element={<PortfolioPage />} />
          <Route
            path="admin"
            element={<RequireRole roles={['SCENARIO_AUTHOR', 'REVIEWER', 'ADMINISTRATOR']}><AdminConsolePage /></RequireRole>}
          />
          <Route
            path="admin/scenarios"
            element={
              <RequireRole roles={['SCENARIO_AUTHOR', 'ADMINISTRATOR']}>
                <ScenarioBuilderPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/achievements"
            element={
              <RequireRole roles={['ADMINISTRATOR']}>
                <AchievementBuilderPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/users"
            element={<RequireRole roles={['ADMINISTRATOR']}><UserManagementPage /></RequireRole>}
          />
          <Route
            path="admin/ai-operations"
            element={<RequireRole roles={['REVIEWER', 'ADMINISTRATOR']}><AiOperationsPage /></RequireRole>}
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}

