import { LogOut } from 'lucide-react'
import { useNavigate } from '@tanstack/react-router'
import { toast } from 'sonner'
import { Button } from '@/shared/ui/button'
import { useSessionStore } from '@/shared/lib/session-store'

export function LogoutButton() {
  const navigate = useNavigate()
  const session = useSessionStore((state) => state.session)
  const clearSession = useSessionStore((state) => state.clearSession)

  if (!session) {
    return null
  }

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label="Sair"
      onClick={() => {
        clearSession()
        toast.success('Sessão encerrada')
        void navigate({ to: '/login' })
      }}
    >
      <LogOut />
    </Button>
  )
}
