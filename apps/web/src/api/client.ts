import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { invalidateCurrentSession, shouldInvalidateSession } from '@/api/authSession'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

const apiClient = axios.create({
  baseURL: apiBaseUrl,
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT token to every request
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Redirect to login on 401
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const authorization = error.config?.headers?.Authorization as string | undefined
      const currentToken = useAuthStore.getState().token

      // Only the request authenticated with the current token may invalidate the
      // current session. Anonymous login failures and late responses carrying an
      // older token must not erase a newer successful login.
      if (currentToken && shouldInvalidateSession(error.response.status, authorization, currentToken)) {
        if (invalidateCurrentSession(currentToken)) {
          console.warn('Session invalidated after API rejected the current token', {
            method: error.config?.method,
            url: error.config?.url,
          })
        }
      }
    }
    return Promise.reject(error)
  }
)

export default apiClient
