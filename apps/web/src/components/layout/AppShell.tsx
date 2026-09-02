import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import {
  Header,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  HeaderMenuButton,
  HeaderGlobalBar,
  HeaderGlobalAction,
  SkipToContent,
  Content,
} from '@carbon/react'
import { Logout } from '@carbon/icons-react'
import { useAuthStore } from '@/store/authStore'
import NotificationPopup from '@/components/shared/NotificationPopup'
import NotificationBell from '@/components/shared/NotificationBell'
import EngagementHUD from '@/lifecycle/components/EngagementHUD'
import styles from '@/lifecycle/lifecycle.module.scss'

export default function AppShell() {
  const { displayName, role, logout } = useAuthStore()
  const location = useLocation()
  const navigate = useNavigate()
  const canAccessAdmin = role === 'SCENARIO_AUTHOR' || role === 'REVIEWER' || role === 'ADMINISTRATOR'
  const usesFixedCanvas = /^\/dashboard\/engagements\/[^/]+\/(intelligence|outreach|preparation|proposal)$/.test(location.pathname)
    || /^\/dashboard\/engagements\/[^/]+\/meetings\/[^/]+$/.test(location.pathname)
  // Carbon hides HeaderNavigation below 1056px and hides this button above it,
  // so exactly one of the two is on screen at any width.
  const [navOpen, setNavOpen] = useState(false)

  // Declared once and rendered twice — the wide header bar and the narrow panel
  // can never drift apart.
  const links = [
    { to: '/dashboard', label: 'Command Centre', end: true },
    { to: '/dashboard/portfolio', label: 'Portfolio', end: false },
    ...(canAccessAdmin ? [{ to: '/dashboard/admin', label: 'Admin Console', end: false }] : []),
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
          isActive={navOpen}
          onClick={() => setNavOpen((open) => !open)}
        />
        <HeaderName href="/dashboard" prefix="IBM">
          Consulting Sim
        </HeaderName>
        <HeaderNavigation aria-label="Main navigation">
          {links.map((link) => (
            <HeaderMenuItem key={link.to} as={NavLink} to={link.to} end={link.end}>
              {link.label}
            </HeaderMenuItem>
          ))}
        </HeaderNavigation>
        <HeaderGlobalBar>
          <NotificationBell />
          <HeaderGlobalAction
            aria-label={`Logout ${displayName ?? ''}`}
            tooltipAlignment="end"
            onClick={handleLogout}
          >
            <Logout size={20} />
          </HeaderGlobalAction>
        </HeaderGlobalBar>
      </Header>
      <NotificationPopup />
      {navOpen && (
        <nav className={styles.mobileNav} aria-label="Main navigation">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) =>
                `${styles.mobileNavLink} ${isActive ? styles.mobileNavLinkActive : ''}`
              }
              onClick={() => setNavOpen(false)}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      )}

      {/* The HUD owns the fixed-header offset through its sticky inset. Keeping
          the shell itself at the document origin prevents a duplicate 3rem
          spacer above every engagement workspace. */}
      <div className={styles.fixedShellRoot}>
        <EngagementHUD />
        <Content className={`${styles.shellContent} ${usesFixedCanvas ? styles.fixedShellContent : ''}`}>
          <Outlet />
        </Content>
      </div>
    </>
  )
}
