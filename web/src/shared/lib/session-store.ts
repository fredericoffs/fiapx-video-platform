import { create } from 'zustand'

export type Role = 'USER' | 'ADMIN'

export interface Session {
  token: string
  email: string
  role: Role
}

interface SessionState {
  session: Session | null
  setSession: (session: Session) => void
  clearSession: () => void
}

/**
 * Estado de sessão (JWT + usuário autenticado) — deixei deliberadamente fora do cache de
 * dados de API (TanStack Query). Nunca persisto em localStorage/sessionStorage:
 * o JWT só vive em memória, decisão de segurança da Sprint 5 (mitiga roubo de
 * token via XSS). O interceptor do cliente de API (shared/api/client.ts) lê o
 * token direto daqui via getState(), fora da árvore React.
 */
export const useSessionStore = create<SessionState>((set) => ({
  session: null,
  setSession: (session) => {
    set({ session })
  },
  clearSession: () => {
    set({ session: null })
  },
}))
