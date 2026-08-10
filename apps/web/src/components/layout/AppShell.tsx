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
  SkipToContent,
  Content,
} from '@carbon/react'
import { Logout } from '@carbon/icons-react'
import { useAuthStore } from '@/store/authStore'
import GameHUD from '@/game/components/GameHUD'
import styles from '@/game/styles/game.module.scss'

export default function AppShell() {
  const { displayName, role, logout } = useAuthStore()
  const navigate = useNavigate()
  // Carbon hides HeaderNavigation below 1056px, and hides this button above it,
  // so exactly one of the two is on screen at any width.
  const [navOpen, setNavOpen] = useState(false)
  const canAccessAdmin = role === 'SCENARIO_AUTHOR' || role === 'REVIEWER' || role === 'ADMINISTRATOR'

  // Declared once and rendered twice — the wide header bar and the narrow
  // panel can never drift apart.
  const links = [
    { to: '/dashboard/world', label: 'Office floor', end: false },
    { to: '/dashboard', label: 'Command Centre', end: true },
    { to: '/dashboard/portfolio', label: 'Portfolio', end: false },
    ...(canAccessAdmin ? [{ to: '/dashboard/admin', label: 'Admin Console', end: false }] : []),
  ]

  const navItems = links.map((link) => (
    <HeaderMenuItem key={link.to} as={NavLink} to={link.to} end={link.end}>
      {link.label}
    </HeaderMenuItem>
  ))

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
        <HeaderNavigation aria-label="Main navigation">{navItems}</HeaderNavigation>
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
