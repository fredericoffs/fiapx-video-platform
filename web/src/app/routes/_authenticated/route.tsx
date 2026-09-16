import { useEffect } from 'react'
import { createFileRoute, Outlet, redirect, useNavigate } from '@tanstack/react-router'
import { useSessionStore } from '@/shared/lib/session-store'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: ({ location }) => {
    const session = useSessionStore.getState().session
    if (!session) {
      redirect({ to: '/login', throw: true })
      return
    }
    if (session.mustChangePassword && location.pathname !== '/change-password') {
      redirect({ to: '/change-password', throw: true })
    }
  },
  component: AuthenticatedLayout,
})

// beforeLoad só roda ao navegar — não pega a sessão sendo limpa enquanto o usuário já está
// numa página (token expirou/foi revogado no meio do uso, ver apiClient.onResponse). Sem
// isso, a tela ficava com dado velho até o usuário tentar navegar de novo.
function AuthenticatedLayout() {
  const session = useSessionStore((state) => state.session)
  const navigate = useNavigate()

  useEffect(() => {
    if (!session) {
      void navigate({ to: '/login' })
    }
  }, [session, navigate])

  return <Outlet />
}
