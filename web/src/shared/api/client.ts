import createClient from 'openapi-fetch'
import type { paths } from '@/shared/api/schema.gen'
import { useSessionStore } from '@/shared/lib/session-store'

// window.__ENV__ é injetado em runtime pelo entrypoint do container (docker-entrypoint.sh),
// permitindo a mesma imagem 'web' apontar pra URLs diferentes por cluster (Oracle, AWS, ...)
// sem rebuild. VITE_API_BASE_URL (build-time) fica só como fallback pro dev local.
const nonEmpty = (value: string | undefined) => (value && value.length > 0 ? value : undefined)

export const baseUrl =
  nonEmpty(window.__ENV__?.API_BASE_URL) ??
  nonEmpty(import.meta.env.VITE_API_BASE_URL) ??
  'http://localhost:8080'

export const apiClient = createClient<paths>({ baseUrl })

apiClient.use({
  onRequest({ request }) {
    const token = useSessionStore.getState().session?.token
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    return request
  },
})
