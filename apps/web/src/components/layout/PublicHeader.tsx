import { Link } from 'react-router-dom'
import { Button } from '@carbon/react'

interface PublicHeaderProps {
  /** Optional call-to-action override for the primary button (defaults to Sign up). */
  hideActions?: boolean
}

/**
 * Marketing-site header shown on the landing, login and register pages.
 * Distinct from the authenticated AppShell header (which uses Carbon's UI
 * Shell navigation) since the public pages need plain text CTA buttons
 * rather than the icon-only global actions bar.
 */
export default function PublicHeader({ hideActions }: PublicHeaderProps) {
  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '1rem 2rem',
        borderBottom: '1px solid #e0e0e0',
        background: '#ffffff',
      }}
    >
      <Link
        to="/"
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: '0.5rem',
          textDecoration: 'none',
        }}
      >
        <span style={{ color: '#0f62fe', fontWeight: 700, fontSize: '1.25rem' }}>IBM</span>
        <span style={{ color: '#161616', fontSize: '1.125rem' }}>Consulting Simulation</span>
      </Link>

      {!hideActions && (
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <Button as={Link} to="/login" kind="ghost" size="md">
            Log in
          </Button>
          <Button as={Link} to="/register" kind="primary" size="md">
            Sign up
          </Button>
        </div>
      )}
    </header>
  )
}
