import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { Button, Form, Heading, InlineNotification, Stack, TextInput, Tile } from '@carbon/react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useConfirmVerification, useResendVerification } from '@/api/hooks/useAuth'
import PublicHeader from '@/components/layout/PublicHeader'

const emailSchema = z.object({ email: z.string().email('Enter a valid email address') })
type EmailForm = z.infer<typeof emailSchema>

export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const location = useLocation()
  const token = params.get('token')
  const alreadyConfirmed = params.get('confirmed') === '1'
  const navigationState = location.state as { accountCreated?: boolean; email?: string } | null
  const emailFromRegistration = params.get('email')
    ?? navigationState?.email
    ?? sessionStorage.getItem('pendingVerificationEmail')
    ?? ''
  const {
    mutate: confirmVerification,
    isError: confirmationFailed,
    isPending: confirmationPending,
    isSuccess: confirmationSucceeded,
  } = useConfirmVerification()
  const resend = useResendVerification()
  const startedToken = useRef<string | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const form = useForm<EmailForm>({
    resolver: zodResolver(emailSchema),
    defaultValues: { email: emailFromRegistration },
  })

  useEffect(() => {
    if (!token || startedToken.current === token) return
    startedToken.current = token
    confirmVerification(token, {
      onSuccess: () => {
        setConfirmed(true)
        sessionStorage.removeItem('pendingVerificationEmail')
        window.history.replaceState(null, document.title, '/verify-email?confirmed=1')
      },
    })
  }, [token, confirmVerification])

  const requestAnother = (values: EmailForm) => resend.mutate(values.email)
  const verificationSucceeded = alreadyConfirmed || confirmed || confirmationSucceeded
  const confirmationProblem = Boolean(token && !verificationSucceeded && confirmationFailed)

  return (
    <AuthFrame title="Confirm your email">
      <Stack gap={5}>
        {token && !verificationSucceeded && !confirmationProblem && confirmationPending && <InlineNotification kind="info" title="Confirming your email" subtitle="This will only take a moment." hideCloseButton />}
        {verificationSucceeded && <InlineNotification kind="success" title="Email confirmed" subtitle="Your account is active. You can now sign in." hideCloseButton />}
        {confirmationProblem && <InlineNotification kind="error" title="This confirmation link is unavailable" subtitle="It may have expired or already been used. Request a new one below." hideCloseButton />}
        {navigationState?.accountCreated && !token && (
          <InlineNotification
            kind="success"
            title="Account created"
            subtitle="Your confirmation email is being sent. Check your inbox and spam folder shortly."
            hideCloseButton
          />
        )}
        {!token && !verificationSucceeded && <p style={{ margin: 0, color: '#525252' }}>Check your inbox for the confirmation link. You must confirm before signing in.</p>}
        {verificationSucceeded && <Button as={Link} to="/login">Continue to sign in</Button>}
        {(!token || confirmationProblem) && (
          <Form onSubmit={form.handleSubmit(requestAnother)}>
            <Stack gap={5}>
              <TextInput id="resend-email" type="email" labelText="Email address" invalid={Boolean(form.formState.errors.email)} invalidText={form.formState.errors.email?.message} {...form.register('email')} />
              {resend.isSuccess && <InlineNotification kind="success" title="Request received" subtitle="If the address is eligible, a new confirmation email will arrive shortly." hideCloseButton />}
              {resend.isError && <InlineNotification kind="error" title="Email could not be sent" subtitle="Please wait a moment and try again." hideCloseButton />}
              <Button type="submit" disabled={resend.isPending}>{resend.isPending ? 'Sending...' : 'Send a new confirmation link'}</Button>
            </Stack>
          </Form>
        )}
        <p style={{ color: '#525252', fontSize: '0.875rem', margin: 0 }}><Link to="/login" style={{ color: '#0f62fe' }}>Back to sign in</Link></p>
      </Stack>
    </AuthFrame>
  )
}

export function AuthFrame({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', background: '#ffffff' }}>
      <PublicHeader hideActions />
      <main style={{ minHeight: 'calc(100vh - 65px)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '2rem', background: 'linear-gradient(135deg, #ffffff 0%, #edf5ff 100%)' }}>
        <Tile style={{ width: '100%', maxWidth: '440px', padding: '2rem' }}>
          <Stack gap={6}><Heading>{title}</Heading>{children}</Stack>
        </Tile>
      </main>
    </div>
  )
}
