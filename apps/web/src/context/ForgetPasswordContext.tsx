import {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from 'react'

type ForgotPasswordContextType = {
  email: string
  resetToken: string

  setEmail: (email: string) => void
  setResetToken: (token: string) => void

  clearForgotPasswordState: () => void
}

const ForgotPasswordContext =
  createContext<ForgotPasswordContextType | undefined>(undefined)

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

export function useForgotPasswordContext() {
  const context = useContext(ForgotPasswordContext)

  if (!context) {
    throw new Error(
      'useForgotPasswordContext must be used inside ForgotPasswordProvider'
    )
  }

  return context
}