import { useContext } from 'react'
import { ForgotPasswordContext } from './ForgotPasswordContext.ts'

export function useForgotPasswordContext() {
  const context = useContext(ForgotPasswordContext)

  if (!context) {
    throw new Error(
      'useForgotPasswordContext must be used inside ForgotPasswordProvider'
    )
  }

  return context
}