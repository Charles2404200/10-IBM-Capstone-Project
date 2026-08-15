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
import { useVerifyOtp } from '@/api/hooks/useAuth'
import { useForgotPasswordContext } from '@/context/forgot-password/useForgotPasswordContext'
import PublicHeader from '@/components/layout/PublicHeader'

const schema = z.object({
  otp: z
    .string()
    .min(1, 'Verification code is required')
    .regex(/^\d+$/, 'Verification code must contain only numbers'),
})

type FormValues = z.infer<typeof schema>

export default function VerifyOtpPage() {
  const navigate = useNavigate()

  const verifyOtp = useVerifyOtp()

  const {
    email,
    setResetToken,
  } = useForgotPasswordContext()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  })

  const onSubmit = (data: FormValues) => {
    verifyOtp.mutate(
      {
        email,
        otp: Number(data.otp),
      },
      {
        onSuccess: (response) => {
          setResetToken(response.token)
          navigate('/change-password')
        },
      }
    )
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
            <Heading>Verify code</Heading>

            <p
              style={{
                color: '#525252',
                fontSize: '0.875rem',
              }}
            >
              Enter the verification code sent to{' '}
              <strong>{email}</strong>.
            </p>

            <Form onSubmit={handleSubmit(onSubmit)}>
              <Stack gap={5}>
                <TextInput
                  id="otp"
                  type="text"
                  inputMode="numeric"
                  labelText="Verification code"
                  invalid={Boolean(errors.otp)}
                  invalidText={errors.otp?.message}
                  {...register('otp')}
                />

                {verifyOtp.isError && (
                  <InlineNotification
                    kind="error"
                    title="Verification failed"
                    subtitle="The verification code is invalid or has expired."
                    hideCloseButton
                  />
                )}

                <Button
                  type="submit"
                  disabled={verifyOtp.isPending}
                  style={{ width: '100%' }}
                >
                  {verifyOtp.isPending
                    ? 'Verifying…'
                    : 'Verify code'}
                </Button>

                <p
                  style={{
                    color: '#525252',
                    fontSize: '0.875rem',
                  }}
                >
                  Wrong email?{' '}
                  <Link
                    to="/forgot-password"
                    style={{
                      color: '#0f62fe',
                    }}
                  >
                    Go back
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