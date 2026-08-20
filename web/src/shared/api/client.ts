import createClient from 'openapi-fetch'
import type { paths } from '@/shared/api/schema.gen'
import { useSessionStore } from '@/shared/lib/session-store'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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
