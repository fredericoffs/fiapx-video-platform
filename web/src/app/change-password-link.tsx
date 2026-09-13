import { KeyRound } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { Button } from '@/shared/ui/button'
import { useSessionStore } from '@/shared/lib/session-store'

export function ChangePasswordLink() {
  const session = useSessionStore((state) => state.session)

  if (!session) {
    return null
  }

  return (
    <Button variant="ghost" size="icon" aria-label="Trocar senha" asChild>
      <Link to="/change-password">
        <KeyRound />
      </Link>
    </Button>
  )
}
