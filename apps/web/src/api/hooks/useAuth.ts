import { useMutation } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { TokenResponse } from '@/api/types'
import { useAuthStore } from '@/store/authStore'
import { ChangePasswordRequest, ForgotPasswordRequest, ResetTokenResponse, VerifyOtpRequest } from '../forget-password.types'

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
  const login = useAuthStore((s) => s.login)
  return useMutation({
    mutationFn: async (data: { email: string; password: string; displayName: string }) => {
      const res = await apiClient.post<TokenResponse>('/api/v1/auth/register', data)
      return res.data
    },
    onSuccess: (data) => login(data),
  })
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: async (data: ForgotPasswordRequest) => {
      const res = await apiClient.post(
        '/api/v1/auth/forgot-password',
        data
      )

      return res.data
    },
  })
}

export function useVerifyOtp() {
  return useMutation({
    mutationFn: async (data: VerifyOtpRequest) => {
      const res = await apiClient.post<ResetTokenResponse>(
        '/api/v1/auth/verify-otp',
        data
      )

      return res.data
    },
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: async (data: ChangePasswordRequest) => {
      const res = await apiClient.post(
        '/api/v1/auth/change-password',
        data
      )

      return res.data
    },
  })
}