import { useMutation } from "@tanstack/react-query"
import { ChangePasswordRequest, ForgotPasswordRequest, ResetTokenResponse, VerifyOtpRequest } from "../types"
import apiClient from "../client"

export function useForgotPassword() {
  return useMutation({
    mutationFn: async (data: ForgotPasswordRequest) => {
      const res = await apiClient.post(
        '/api/v1/forgot-password/verify-email',
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
        '/api/v1/forgot-password/verify-otp',
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
        '/api/v1/forgot-password/change-password',
        data
      )

      return res.data
    },
  })
}