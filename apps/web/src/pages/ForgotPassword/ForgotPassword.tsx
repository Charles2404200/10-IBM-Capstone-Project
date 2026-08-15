import { useNavigate, Link } from 'react-router-dom'
import {
  Form,
  Stack,
  TextInput,
  Button,
  InlineNotification,
  Tile,
  Heading,
} from '@carbon/react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useForgotPassword } from '@/api/hooks/useForgotPassword'
import { useForgotPasswordContext } from '@/context/forgot-password/useForgotPasswordContext'
import PublicHeader from '@/components/layout/PublicHeader'

const schema = z.object({
  email: z.string().email('Enter a valid email'),
})

type FormValues = z.infer<typeof schema>

export default function ForgotPasswordPage() {
  const navigate = useNavigate()

  const forgotPassword = useForgotPassword()
  const { setEmail } = useForgotPasswordContext()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  })

  const onSubmit = (data: FormValues) => {
    forgotPassword.mutate(data, {
      onSuccess: () => {
        setEmail(data.email)
        navigate('/verify-otp')
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
        <Tile
          style={{
            width: '100%',
            maxWidth: '400px',
            padding: '2rem',
          }}
        >
          <Stack gap={6}>
            <Heading>Forgot password</Heading>

            <p
              style={{
                color: '#525252',
                fontSize: '0.875rem',
              }}
            >
              Enter your email address and we will send you a verification code.
            </p>

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

                {forgotPassword.isError && (
                  <InlineNotification
                    kind="error"
                    title="Request failed"
                    subtitle="Unable to send the verification code. Please try again."
                    hideCloseButton
                  />
                )}

                <Button
                  type="submit"
                  disabled={forgotPassword.isPending}
                  style={{ width: '100%' }}
                >
                  {forgotPassword.isPending
                    ? 'Sending code…'
                    : 'Send verification code'}
                </Button>

                <p
                  style={{
                    color: '#525252',
                    fontSize: '0.875rem',
                  }}
                >
                  Remember your password?{' '}
                  <Link
                    to="/login"
                    style={{
                      color: '#0f62fe',
                    }}
                  >
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