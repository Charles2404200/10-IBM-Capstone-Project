import { useNavigate } from 'react-router-dom'
import {
  Form,
  Stack,
  PasswordInput,
  Button,
  InlineNotification,
  Tile,
  Heading,
} from '@carbon/react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useChangePassword } from '@/api/hooks/useForgotPassword'
import { useForgotPasswordContext } from '@/context/forgot-password/useForgotPasswordContext'
import PublicHeader from '@/components/layout/PublicHeader'
import axios from 'axios'

const schema = z
  .object({
    password: z
      .string()
      .min(8, 'Password must be at least 8 characters'),

    confirmPassword: z
      .string()
      .min(1, 'Confirm your password'),
  })
  .refine(
    (data) => data.password === data.confirmPassword,
    {
      message: 'Passwords do not match',
      path: ['confirmPassword'],
    }
  )

type FormValues = z.infer<typeof schema>

type ProblemDetail = {
  type: string
  title: string
  status: number
  detail: string
  instance: string
}

export default function ChangePasswordPage() {
  const navigate = useNavigate()

  const changePassword = useChangePassword()

  const {
    resetToken,
    clearForgotPasswordState,
    email
  } = useForgotPasswordContext()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  })

  const onSubmit = (data: FormValues) => {
    changePassword.mutate(
      {
        resetToken,
        password: data.password,
        repeatPassword: data.confirmPassword,
        email
      },
      {
        onSuccess: () => {
          clearForgotPasswordState()
          navigate('/login')
        },
      }
    )
  }

  const errorMessage =
    axios.isAxiosError<ProblemDetail>(changePassword.error)
      ? changePassword.error.response?.data?.detail
      : undefined

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
            <Heading>Change password</Heading>

            <p
              style={{
                color: '#525252',
                fontSize: '0.875rem',
              }}
            >
              Enter a new password for your account.
            </p>

            <Form onSubmit={handleSubmit(onSubmit)}>
              <Stack gap={5}>
                <PasswordInput
                  id="password"
                  labelText="New password"
                  invalid={Boolean(errors.password)}
                  invalidText={errors.password?.message}
                  {...register('password')}
                />

                <PasswordInput
                  id="confirmPassword"
                  labelText="Confirm new password"
                  invalid={Boolean(errors.confirmPassword)}
                  invalidText={errors.confirmPassword?.message}
                  {...register('confirmPassword')}
                />

                {changePassword.isError && (
                  <InlineNotification
                    kind="error"
                    title="Password change failed"
                    subtitle={
                      errorMessage ??
                      'Unable to change password. Please try again.'
                    }
                    hideCloseButton
                  />
                )}

                {changePassword.isSuccess && (
                  <InlineNotification
                    kind="success"
                    title="Password changed"
                    subtitle="Your password has been changed successfully."
                    hideCloseButton
                  />
                )}

                <Button
                  type="submit"
                  disabled={changePassword.isPending}
                  style={{ width: '100%' }}
                >
                  {changePassword.isPending
                    ? 'Changing password…'
                    : 'Change password'}
                </Button>
              </Stack>
            </Form>
          </Stack>
        </Tile>
      </div>
    </div>
  )
}