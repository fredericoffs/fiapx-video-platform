import { toast } from 'sonner'
import { Trash2 } from 'lucide-react'
import {
  useAdminUsersQuery,
  useDeleteUserMutation,
  type AdminUser,
} from '@/features/admin/api/queries'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'

export function AdminUsersTable() {
  const usersQuery = useAdminUsersQuery()
  const deleteMutation = useDeleteUserMutation()

  const handleDelete = (user: AdminUser) => {
    if (
      !confirm(
        `Excluir o usuário "${user.email}"? Todos os vídeos dele também serão excluídos. Essa ação não pode ser desfeita.`,
      )
    ) {
      return
    }
    deleteMutation.mutate(user.id, {
      onSuccess: () => {
        toast.success('Usuário excluído')
      },
      onError: (error: unknown) => {
        toast.error(error instanceof Error ? error.message : 'Não foi possível excluir o usuário')
      },
    })
  }

  if (usersQuery.isPending) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
      </div>
    )
  }

  if (usersQuery.isError) {
    return <p className="text-sm text-destructive">Não foi possível carregar os usuários.</p>
  }

  return (
    <div className="overflow-x-auto border">
      <table className="w-full text-left text-sm">
        <thead className="border-b bg-muted/50">
          <tr>
            <th className="px-4 py-2 font-medium">E-mail</th>
            <th className="px-4 py-2 font-medium">Papel</th>
            <th className="px-4 py-2 font-medium">Cadastrado em</th>
            <th className="px-4 py-2" />
          </tr>
        </thead>
        <tbody>
          {usersQuery.data.map((user) => (
            <tr key={user.id} className="border-b last:border-b-0">
              <td className="px-4 py-2">{user.email}</td>
              <td className="px-4 py-2">
                <Badge variant={user.role === 'ADMIN' ? 'default' : 'secondary'}>{user.role}</Badge>
              </td>
              <td className="px-4 py-2 text-muted-foreground">
                {new Date(user.createdAt).toLocaleString('pt-BR')}
              </td>
              <td className="px-4 py-2 text-right">
                <Button
                  size="icon-sm"
                  variant="outline"
                  disabled={deleteMutation.isPending}
                  onClick={() => {
                    handleDelete(user)
                  }}
                  aria-label={`Excluir ${user.email}`}
                >
                  <Trash2 />
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {usersQuery.data.length === 0 && (
        <p className="px-4 py-6 text-center text-sm text-muted-foreground">
          Nenhum usuário cadastrado.
        </p>
      )}
    </div>
  )
}
