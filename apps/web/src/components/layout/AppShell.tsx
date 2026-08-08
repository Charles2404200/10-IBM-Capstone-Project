import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import {
  Header,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  HeaderGlobalBar,
  HeaderGlobalAction,
  SkipToContent,
  Content,
} from '@carbon/react'
import { Logout } from '@carbon/icons-react'
import { useAuthStore } from '@/store/authStore'

export default function AppShell() {
  const { displayName, role, logout } = useAuthStore()
  const navigate = useNavigate()
  const canManageScenarios = role === 'SCENARIO_AUTHOR' || role === 'ADMINISTRATOR'
  const canManageAchievements = role === 'ADMINISTRATOR'

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <>
      <SkipToContent />
      <Header aria-label="IBM Consulting Simulation">
        <HeaderName href="/dashboard" prefix="IBM">
          Consulting Sim
        </HeaderName>
        <HeaderNavigation aria-label="Main navigation">
          <HeaderMenuItem as={NavLink} to="/dashboard">
            Command Centre
          </HeaderMenuItem>
          <HeaderMenuItem as={NavLink} to="/dashboard/portfolio">
            Portfolio
          </HeaderMenuItem>
          {canManageScenarios && (
            <HeaderMenuItem as={NavLink} to="/dashboard/admin/scenarios">
              Scenario Builder
            </HeaderMenuItem>
          )}
          {canManageAchievements && (
            <HeaderMenuItem as={NavLink} to="/dashboard/admin/achievements">
              Achievement Builder
            </HeaderMenuItem>
          )}
        </HeaderNavigation>
        <HeaderGlobalBar>
          <HeaderGlobalAction
            aria-label={`Logout ${displayName ?? ''}`}
            tooltipAlignment="end"
            onClick={handleLogout}
          >
            <Logout size={20} />
          </HeaderGlobalAction>
        </HeaderGlobalBar>
      </Header>
      <Content>
        <Outlet />
      </Content>
    </>
  )
}
