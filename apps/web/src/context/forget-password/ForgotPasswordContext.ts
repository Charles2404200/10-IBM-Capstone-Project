import { createContext } from 'react'

export type ForgotPasswordContextType = {
  email: string
  resetToken: string
  setEmail: (email: string) => void
  setResetToken: (token: string) => void
  clearForgotPasswordState: () => void
}

export const ForgotPasswordContext =
  createContext<ForgotPasswordContextType | undefined>(undefined)