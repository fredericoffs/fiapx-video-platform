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
import { Input } from '@/shared/ui/input'
import { Skeleton } from '@/shared/ui/skeleton'

export function AdminUsersTable() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState<PageSize>(10)
  const [email, setEmail] = useState('')
  const usersQuery = useAdminUsersQuery(page, size, email)
  const deleteMutation = useDeleteUserMutation()
  const queryClient = useQueryClient()

  const handleSizeChange = (newSize: PageSize) => {
    setSize(newSize)
    setPage(0)
  }

  const handleEmailChange = (value: string) => {
    setEmail(value)
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

  return (
    <div className="flex flex-col gap-3">
      <Input
        type="search"
        placeholder="Filtrar por e-mail…"
        aria-label="Filtrar usuários por e-mail"
        className="max-w-xs"
        value={email}
        onChange={(event) => {
          handleEmailChange(event.target.value)
        }}
      />

      {usersQuery.isPending && (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      )}

      {usersQuery.isError && (
        <p className="text-sm text-destructive">Não foi possível carregar os usuários.</p>
      )}

      {usersQuery.data && (
        <>
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
                {usersQuery.data.items.map((user) => (
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
            {usersQuery.data.items.length === 0 && (
              <p className="px-4 py-6 text-center text-sm text-muted-foreground">
                Nenhum usuário encontrado.
              </p>
            )}
          </div>

          <AdminPagination
            page={page}
            size={size}
            totalElements={usersQuery.data.totalElements}
            onPageChange={setPage}
            onSizeChange={handleSizeChange}
          />
        </>
      )}
    </div>
  )
}
