import { toast } from 'sonner'
import { queryClient } from '@/app/query-client'
import { useSessionStore } from './session-store'

export function expireSession(token: string | undefined) {
  if (!token || useSessionStore.getState().session?.token !== token) return
  useSessionStore.getState().clearSession()
  queryClient.clear()
  toast.error('Sua sessão expirou. Faça login novamente.')
}
