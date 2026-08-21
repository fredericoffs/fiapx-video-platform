import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Trash2 } from 'lucide-react'
import {
  adminKeys,
  useAdminUsersQuery,
  useDeleteUserMutation,
  type AdminUser,
  type PageSize,
} from '@/features/admin/api/queries'
import { AdminPagination } from '@/features/admin/components/admin-pagination'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'

export function AdminUsersTable() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState<PageSize>(10)
  const usersQuery = useAdminUsersQuery(page, size)
  const deleteMutation = useDeleteUserMutation()
  const queryClient = useQueryClient()

  const handleSizeChange = (newSize: PageSize) => {
    setSize(newSize)
    setPage(0)
  }

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
        void queryClient.invalidateQueries({ queryKey: adminKeys.all })
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

  const { items, totalElements } = usersQuery.data

  return (
    <div className="flex flex-col gap-3">
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
            {items.map((user) => (
              <tr key={user.id} className="border-b last:border-b-0">
                <td className="px-4 py-2">{user.email}</td>
                <td className="px-4 py-2">
                  <Badge variant={user.role === 'ADMIN' ? 'default' : 'secondary'}>
                    {user.role}
                  </Badge>
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
        {items.length === 0 && (
          <p className="px-4 py-6 text-center text-sm text-muted-foreground">
            Nenhum usuário cadastrado.
          </p>
        )}
      </div>

      <AdminPagination
        page={page}
        size={size}
        totalElements={totalElements}
        onPageChange={setPage}
        onSizeChange={handleSizeChange}
      />
    </div>
  )
}
