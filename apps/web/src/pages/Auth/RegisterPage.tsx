import { useNavigate, Link } from 'react-router-dom'
import {
  Form,
  Stack,
  TextInput,
  PasswordInput,
  Button,
  InlineNotification,
  Tile,
  Heading,
} from '@carbon/react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useRegister } from '@/api/hooks/useAuth'
import PublicHeader from '@/components/layout/PublicHeader'

const schema = z.object({
  displayName: z.string().min(2, 'Name must be at least 2 characters'),
  email: z.string().email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
})

type FormValues = z.infer<typeof schema>

export default function RegisterPage() {
  const navigate = useNavigate()
  const register_ = useRegister()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = (data: FormValues) => {
    register_.mutate(data, {
      onSuccess: (response) => {
        sessionStorage.setItem('pendingVerificationEmail', response.email)
        navigate('/verify-email', {
          replace: true,
          state: { accountCreated: true, email: response.email },
        })
      },
    })
  }

  return (
    <div style={{ minHeight: '100vh', background: '#ffffff' }}>
      <PublicHeader hideActions />
      <div
        style={{
          minHeight: 'calc(100vh - 65px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '2rem',
          background: 'linear-gradient(135deg, #ffffff 0%, #edf5ff 100%)',
        }}
      >
        <Tile style={{ width: '100%', maxWidth: '440px', padding: '2rem' }}>
          <Stack gap={6}>
            <Heading>Create your account</Heading>
            <Form onSubmit={handleSubmit(onSubmit)}>
              <Stack gap={5}>
                <TextInput
                  id="displayName"
                  labelText="Display Name"
                  invalid={Boolean(errors.displayName)}
                  invalidText={errors.displayName?.message}
                  {...register('displayName')}
                />
                <TextInput
                  id="email"
                  type="email"
                  labelText="Email"
                  invalid={Boolean(errors.email)}
                  invalidText={errors.email?.message}
                  {...register('email')}
                />
                <PasswordInput
                  id="password"
                  labelText="Password"
                  invalid={Boolean(errors.password)}
                  invalidText={errors.password?.message}
                  {...register('password')}
                />
                {register_.isError && (
                  <InlineNotification
                    kind="error"
                    title="Registration failed"
                    subtitle="We could not create your account. Check the details and try again."
                    hideCloseButton
                  />
                )}
                <Button type="submit" disabled={register_.isPending} style={{ width: '100%' }}>
                  {register_.isPending ? 'Creating account…' : 'Create Account'}
                </Button>
                <p style={{ color: '#525252', fontSize: '0.875rem' }}>
                  Already have an account?{' '}
                  <Link to="/login" style={{ color: '#0f62fe' }}>
                    Sign in
                  </Link>
                </p>
              </Stack>
            </Form>
          </Stack>
        </Tile>
      </div>
    </div>
  )
}
