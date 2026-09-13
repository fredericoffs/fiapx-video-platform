import { createFileRoute, Outlet, redirect } from '@tanstack/react-router'
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
  component: () => <Outlet />,
})
