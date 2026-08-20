import { createFileRoute, Outlet, redirect } from '@tanstack/react-router'
import { useSessionStore } from '@/shared/lib/session-store'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: () => {
    const session = useSessionStore.getState().session
    if (!session) {
      redirect({ to: '/login', throw: true })
    }
  },
  component: () => <Outlet />,
})
