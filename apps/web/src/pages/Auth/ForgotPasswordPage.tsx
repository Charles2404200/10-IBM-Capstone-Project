import { Link } from 'react-router-dom'
import { Button, Form, InlineNotification, Stack, TextInput } from '@carbon/react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useRequestPasswordReset } from '@/api/hooks/useAuth'
import { AuthFrame } from './VerifyEmailPage'

const schema = z.object({ email: z.string().email('Enter a valid email address') })
type FormValues = z.infer<typeof schema>

export default function ForgotPasswordPage() {
  const request = useRequestPasswordReset()
  const form = useForm<FormValues>({ resolver: zodResolver(schema) })

  return (
    <AuthFrame title="Reset your password">
      <p style={{ margin: 0, color: '#525252' }}>Enter your email and we will send a reset link if an eligible account exists.</p>
      <Form onSubmit={form.handleSubmit((values) => request.mutate(values.email))}>
        <Stack gap={5}>
          <TextInput id="email" type="email" labelText="Email address" invalid={Boolean(form.formState.errors.email)} invalidText={form.formState.errors.email?.message} {...form.register('email')} />
          {request.isSuccess && <InlineNotification kind="success" title="Request received" subtitle="Check your inbox for a password reset link." hideCloseButton />}
          {request.isError && <InlineNotification kind="error" title="Email could not be sent" subtitle="Please wait a moment and try again." hideCloseButton />}
          <Button type="submit" disabled={request.isPending}>{request.isPending ? 'Sending...' : 'Send reset link'}</Button>
          <p style={{ margin: 0, color: '#525252', fontSize: '0.875rem' }}><Link to="/login" style={{ color: '#0f62fe' }}>Back to sign in</Link></p>
        </Stack>
      </Form>
    </AuthFrame>
  )
}
