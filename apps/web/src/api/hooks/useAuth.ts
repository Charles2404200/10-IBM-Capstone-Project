import { useMutation } from '@tanstack/react-query'
import apiClient from '@/api/client'
import type { TokenResponse } from '@/api/types'
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
  const login = useAuthStore((s) => s.login)
  return useMutation({
    mutationFn: async (data: { email: string; password: string; displayName: string }) => {
      const res = await apiClient.post<TokenResponse>('/api/v1/auth/register', data)
      return res.data
    },
    onSuccess: (data) => login(data),
  })
}

