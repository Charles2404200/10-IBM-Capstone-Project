import { useState, type ReactNode } from 'react'
import { ForgotPasswordContext } from './ForgotPasswordContext.ts'

type ForgotPasswordProviderProps = {
  children: ReactNode
}

export function ForgotPasswordProvider({
  children,
}: ForgotPasswordProviderProps) {
  const [email, setEmail] = useState('')
  const [resetToken, setResetToken] = useState('')

  const clearForgotPasswordState = () => {
    setEmail('')
    setResetToken('')
  }

  return (
    <ForgotPasswordContext.Provider
      value={{
        email,
        resetToken,
        setEmail,
        setResetToken,
        clearForgotPasswordState,
      }}
    >
      {children}
    </ForgotPasswordContext.Provider>
  )
}