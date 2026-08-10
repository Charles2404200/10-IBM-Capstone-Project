import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import {
  Header,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  HeaderMenuButton,
  HeaderGlobalBar,
  HeaderGlobalAction,
  HeaderSideNavItems,
  SideNav,
  SideNavItems,
  SkipToContent,
  Content,
} from '@carbon/react'
import { Logout } from '@carbon/icons-react'
import { useAuthStore } from '@/store/authStore'
import GameHUD from '@/game/components/GameHUD'

export default function AppShell() {
  const { displayName, role, logout } = useAuthStore()
  const navigate = useNavigate()
  // Carbon hides HeaderNavigation below 1056px and expects a SideNav to take
  // over. Without one there was simply no navigation at all on a phone.
  const [navOpen, setNavOpen] = useState(false)
  const canAccessAdmin = role === 'SCENARIO_AUTHOR' || role === 'REVIEWER' || role === 'ADMINISTRATOR'

  const navItems = [
    <HeaderMenuItem key="world" as={NavLink} to="/dashboard/world" onClick={() => setNavOpen(false)}>
      Office floor
    </HeaderMenuItem>,
    <HeaderMenuItem key="command" as={NavLink} to="/dashboard" onClick={() => setNavOpen(false)}>
      Command Centre
    </HeaderMenuItem>,
    <HeaderMenuItem key="portfolio" as={NavLink} to="/dashboard/portfolio" onClick={() => setNavOpen(false)}>
      Portfolio
    </HeaderMenuItem>,
    ...(canAccessAdmin
      ? [
          <HeaderMenuItem key="admin" as={NavLink} to="/dashboard/admin" onClick={() => setNavOpen(false)}>
            Admin Console
          </HeaderMenuItem>,
        ]
      : []),
  ]

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <>
      <SkipToContent />
      <Header aria-label="IBM Consulting Simulation">
        <HeaderMenuButton
          aria-label={navOpen ? 'Close menu' : 'Open menu'}
          isCollapsible
          isActive={navOpen}
          onClick={() => setNavOpen((open) => !open)}
        />
        <HeaderName href="/dashboard" prefix="IBM">
          Consulting Sim
        </HeaderName>
        <HeaderNavigation aria-label="Main navigation">{navItems}</HeaderNavigation>

        <SideNav
          aria-label="Main navigation"
          expanded={navOpen}
          isPersistent={false}
          onSideNavBlur={() => setNavOpen(false)}
        >
          <SideNavItems>
            <HeaderSideNavItems>{navItems}</HeaderSideNavItems>
          </SideNavItems>
        </SideNav>
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
