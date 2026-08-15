export type ForgotPasswordRequest = {
  email: string
}

export type VerifyOtpRequest = {
  email: string
  otp: number
}

export type ChangePasswordRequest = {
  token: string
  password: string
}

export type ResetTokenResponse = {
  token: string
}