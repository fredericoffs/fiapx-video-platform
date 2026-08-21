import { createFileRoute, redirect } from '@tanstack/react-router'
import { useSessionStore } from '@/shared/lib/session-store'
import { AdminPage } from '@/features/admin/components/admin-page'

export const Route = createFileRoute('/_authenticated/admin')({
  beforeLoad: () => {
    const session = useSessionStore.getState().session
    if (session?.role !== 'ADMIN') {
      redirect({ to: '/', throw: true })
    }
  },
  component: AdminPage,
})
