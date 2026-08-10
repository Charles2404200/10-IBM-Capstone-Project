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
import GameHUD from '@/game/components/GameHUD'

export default function AppShell() {
  const { displayName, role, logout } = useAuthStore()
  const navigate = useNavigate()
  const canAccessAdmin = role === 'SCENARIO_AUTHOR' || role === 'REVIEWER' || role === 'ADMINISTRATOR'

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
          <HeaderMenuItem as={NavLink} to="/dashboard/world">
            Office floor
          </HeaderMenuItem>
          <HeaderMenuItem as={NavLink} to="/dashboard">
            Command Centre
          </HeaderMenuItem>
          <HeaderMenuItem as={NavLink} to="/dashboard/portfolio">
            Portfolio
          </HeaderMenuItem>
          {canAccessAdmin && (
            <HeaderMenuItem as={NavLink} to="/dashboard/admin">
              Admin Console
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
      {/* Carbon's Header is position: fixed, and it compensates by giving a
          *sibling* .cds--content a 3rem top margin. Anything else placed
          between the two lands underneath the fixed header and disappears.
          Wrapping the body means that sibling rule no longer applies, so this
          element owns the offset for both children instead. */}
      <div style={{ paddingBlockStart: '3rem' }}>
        {/* Persistent phase stepper and client-relationship meters, so every
            workspace inherits them — previously the stepper appeared on some
            phase pages and not others, and the relationship state was only
            visible inside the live meeting. */}
        <GameHUD />
        <Content>
          <Outlet />
        </Content>
      </div>
    </>
  )
}
