import { ShieldUser } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { Button } from '@/shared/ui/button'
import { useSessionStore } from '@/shared/lib/session-store'

export function AdminLink() {
  const session = useSessionStore((state) => state.session)

  if (session?.role !== 'ADMIN') {
    return null
  }

  return (
    <Button variant="ghost" size="icon" aria-label="Área admin" asChild>
      <Link to="/admin">
        <ShieldUser />
      </Link>
    </Button>
  )
}
