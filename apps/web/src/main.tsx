import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import '@carbon/react/index.scss'

const staleBundleReloadKey = 'consulting-sim:stale-bundle-reload-at'

function reloadForStaleBundle() {
  const previousReloadAt = Number(sessionStorage.getItem(staleBundleReloadKey) ?? '0')
  // Protect against a broken deployment loop, while allowing a later release
  // in the same browser tab to refresh normally.
  if (Date.now() - previousReloadAt < 10_000) return
  sessionStorage.setItem(staleBundleReloadKey, String(Date.now()))
  window.location.reload()
}

window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault()
  reloadForStaleBundle()
})

window.addEventListener('error', (event) => {
  const message = event.message ?? ''
  if (/Failed to fetch dynamically imported module|Loading chunk|Importing a module script failed/i.test(message)) {
    reloadForStaleBundle()
  }
})

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>
)
