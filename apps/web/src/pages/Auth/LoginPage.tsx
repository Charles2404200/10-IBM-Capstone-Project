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
import { useLogin } from '@/api/hooks/useAuth'
import PublicHeader from '@/components/layout/PublicHeader'

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
})

type FormValues = z.infer<typeof schema>

export default function LoginPage() {
  const navigate = useNavigate()
  const login = useLogin()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = (data: FormValues) => {
    login.mutate(data, { onSuccess: () => navigate('/dashboard') })
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
        <Tile style={{ width: '100%', maxWidth: '400px', padding: '2rem' }}>
          <Stack gap={6}>
            <Heading>Sign in</Heading>
            <Form onSubmit={handleSubmit(onSubmit)}>
              <Stack gap={5}>
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
                {login.isError && (
                  <InlineNotification
                    kind="error"
                    title="Login failed"
                    subtitle="Check your credentials and try again."
                    hideCloseButton
                  />
                )}
                <Button type="submit" disabled={login.isPending} style={{ width: '100%' }}>
                  {login.isPending ? 'Signing in…' : 'Sign in'}
                </Button>
                <p style={{ color: '#525252', fontSize: '0.875rem' }}>
                  No account?{' '}
                  <Link to="/register" style={{ color: '#0f62fe' }}>
                    Register
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
