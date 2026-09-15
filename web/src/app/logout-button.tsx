import { LogOut } from 'lucide-react'
import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/shared/ui/button'
import { useSessionStore } from '@/shared/lib/session-store'

export function LogoutButton() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
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
        // Sem isso, dados privados (lista de vídeos) da conta que saiu ficam no cache do
        // TanStack Query e podem aparecer pra próxima conta que logar na mesma aba.
        queryClient.clear()
        toast.success('Sessão encerrada')
        void navigate({ to: '/login' })
      }}
    >
      <LogOut />
    </Button>
  )
}
