import { useMutation } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { RegistrationResponse, TokenResponse } from '@/api/types'
import { useAuthStore } from '@/store/authStore'

export function useLogin() {
  const login = useAuthStore((s) => s.login)
  return useMutation({
    mutationFn: async (data: { email: string; password: string }) => {
      const res = await apiClient.post<TokenResponse>('/api/v1/auth/login', data)
      return res.data
    },
    onSuccess: (data) => login(data),
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: async (data: { email: string; password: string; displayName: string }) => {
      const res = await apiClient.post<RegistrationResponse>('/api/v1/auth/register', data)
      return res.data
    },
  })
}

export function useCompleteOnboarding() {
  const completeOnboarding = useAuthStore((s) => s.completeOnboarding)

  return useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/users/me/onboarding/complete', undefined, { timeout: 5_000 })
    },
    onSuccess: completeOnboarding,
  })
}

export function useResendVerification() {
  return useMutation({
    mutationFn: async (email: string) => {
      await apiClient.post('/api/v1/auth/email-verification/resend', { email })
    },
  })
}

export function useConfirmVerification() {
  return useMutation({
    mutationFn: async (token: string) => {
      await apiClient.post('/api/v1/auth/email-verification/confirm', { token }, { timeout: 15_000 })
    },
  })
}

export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: async (email: string) => {
      await apiClient.post('/api/v1/auth/password-reset/request', { email })
    },
  })
}

export function useConfirmPasswordReset() {
  return useMutation({
    mutationFn: async (data: { token: string; password: string }) => {
      await apiClient.post('/api/v1/auth/password-reset/confirm', data)
    },
  })
}
