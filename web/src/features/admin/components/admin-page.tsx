import { AdminUsersTable } from '@/features/admin/components/admin-users-table'
import { AdminVideosTable } from '@/features/admin/components/admin-videos-table'

export function AdminPage() {
  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-heading text-lg font-bold">Administração</h1>
        <p className="text-sm text-muted-foreground">
          Controle sobre todos os usuários e vídeos cadastrados no sistema.
        </p>
      </div>

      <section className="flex flex-col gap-3">
        <h2 className="font-heading text-base font-medium">Usuários</h2>
        <AdminUsersTable />
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="font-heading text-base font-medium">Vídeos</h2>
        <AdminVideosTable />
      </section>
    </div>
  )
}
