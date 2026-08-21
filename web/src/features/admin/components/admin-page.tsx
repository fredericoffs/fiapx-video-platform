import { useState } from 'react'
import { AdminUsersTable } from '@/features/admin/components/admin-users-table'
import { AdminVideosTable } from '@/features/admin/components/admin-videos-table'
import { Button } from '@/shared/ui/button'

type AdminTab = 'videos' | 'users'

export function AdminPage() {
  const [tab, setTab] = useState<AdminTab>('videos')

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-heading text-lg font-bold">Administração</h1>
        <p className="text-sm text-muted-foreground">
          Controle sobre todos os usuários e vídeos cadastrados no sistema.
        </p>
      </div>

      <div role="tablist" className="flex gap-1 border-b">
        <Button
          role="tab"
          aria-selected={tab === 'videos'}
          variant="ghost"
          className="rounded-none border-b-2 border-transparent aria-selected:border-primary aria-selected:text-foreground"
          onClick={() => {
            setTab('videos')
          }}
        >
          Vídeos
        </Button>
        <Button
          role="tab"
          aria-selected={tab === 'users'}
          variant="ghost"
          className="rounded-none border-b-2 border-transparent aria-selected:border-primary aria-selected:text-foreground"
          onClick={() => {
            setTab('users')
          }}
        >
          Usuários
        </Button>
      </div>

      {tab === 'videos' ? <AdminVideosTable /> : <AdminUsersTable />}
    </div>
  )
}
