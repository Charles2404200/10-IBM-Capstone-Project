import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import AppShell from '@/components/layout/AppShell'
import LandingPage from '@/pages/Landing/LandingPage'
import LoginPage from '@/pages/Auth/LoginPage'
import RegisterPage from '@/pages/Auth/RegisterPage'
import CommandCentrePage from '@/pages/CommandCentre/CommandCentrePage'
import LeadPipelinePage from '@/pages/LeadPipeline/LeadPipelinePage'
import ClientIntelligencePage from '@/pages/ClientIntelligence/ClientIntelligencePage'
import OutreachWorkspacePage from '@/pages/OutreachWorkspace/OutreachWorkspacePage'
import MeetingPreparationPage from '@/pages/MeetingPreparation/MeetingPreparationPage'
import LiveMeetingPage from '@/pages/LiveMeeting/LiveMeetingPage'
import ProposalStudioPage from '@/pages/ProposalStudio/ProposalStudioPage'
import AssessmentReviewPage from '@/pages/AssessmentReview/AssessmentReviewPage'
import PortfolioPage from '@/pages/Portfolio/PortfolioPage'
import ScenarioBuilderPage from '@/pages/Admin/ScenarioBuilderPage'
import AchievementBuilderPage from '@/pages/Admin/AchievementBuilderPage'

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
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

