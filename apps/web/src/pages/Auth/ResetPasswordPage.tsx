import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Form, InlineNotification, PasswordInput, Stack } from '@carbon/react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useConfirmPasswordReset } from '@/api/hooks/useAuth'
import { AuthFrame } from './VerifyEmailPage'

const schema = z.object({
  password: z.string().min(8, 'Password must be at least 8 characters'),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' })
type FormValues = z.infer<typeof schema>

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token')
  const reset = useConfirmPasswordReset()
  const form = useForm<FormValues>({ resolver: zodResolver(schema) })

  if (!token) {
    return <AuthFrame title="Reset your password"><InlineNotification kind="error" title="Reset link missing" subtitle="Request a new password reset link to continue." hideCloseButton /><Link to="/forgot-password" style={{ color: '#0f62fe' }}>Request reset link</Link></AuthFrame>
  }

  return (
    <AuthFrame title="Choose a new password">
      <Form onSubmit={form.handleSubmit((values) => reset.mutate({ token, password: values.password }, { onSuccess: () => navigate('/login') }))}>
        <Stack gap={5}>
          <PasswordInput id="password" labelText="New password" invalid={Boolean(form.formState.errors.password)} invalidText={form.formState.errors.password?.message} {...form.register('password')} />
          <PasswordInput id="confirm-password" labelText="Confirm new password" invalid={Boolean(form.formState.errors.confirmPassword)} invalidText={form.formState.errors.confirmPassword?.message} {...form.register('confirmPassword')} />
          {reset.isError && <InlineNotification kind="error" title="Reset link is unavailable" subtitle="It may have expired or already been used. Request a new link." hideCloseButton />}
          <Button type="submit" disabled={reset.isPending}>{reset.isPending ? 'Saving...' : 'Reset password'}</Button>
          <p style={{ margin: 0, color: '#525252', fontSize: '0.875rem' }}><Link to="/forgot-password" style={{ color: '#0f62fe' }}>Request another reset link</Link></p>
        </Stack>
      </Form>
    </AuthFrame>
  )
}
