import createClient from 'openapi-fetch'
import { toast } from 'sonner'
import type { paths } from '@/shared/api/schema.gen'
import { useSessionStore } from '@/shared/lib/session-store'

// login e change-password respondem 401 por um motivo que não é "sessão inválida" (senha
// errada, senha atual errada) — cada um já trata isso localmente. Em qualquer outra rota,
// 401 só pode significar token expirado ou revogado (item 14).
const LOCALLY_HANDLED_401_PATHS = new Set(['/auth/login', '/users/me/password'])

// window.__ENV__ é injetado em runtime pelo entrypoint do container (docker-entrypoint.sh),
// permitindo a mesma imagem 'web' apontar pra URLs diferentes por cluster (AWS, kind, ...)
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
  onResponse({ response, schemaPath }) {
    if (response.status === 401 && !LOCALLY_HANDLED_401_PATHS.has(schemaPath)) {
      const hadSession = useSessionStore.getState().session !== null
      useSessionStore.getState().clearSession()
      if (hadSession) {
        toast.error('Sua sessão expirou. Faça login novamente.')
      }
    }
    return response
  },
})
