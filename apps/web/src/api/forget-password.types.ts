export type ForgotPasswordRequest = {
  email: string
}

export type VerifyOtpRequest = {
  email: string
  otp: number
}

export type ChangePasswordRequest = {
  resetToken: string
  password: string
  repeatPassword: string
}

export type ResetTokenResponse = {
  message: string
  resetToken: string
}
