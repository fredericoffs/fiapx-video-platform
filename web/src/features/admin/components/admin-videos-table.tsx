import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Trash2 } from 'lucide-react'
import {
  adminKeys,
  useAdminVideosQuery,
  type AdminVideo,
  type PageSize,
} from '@/features/admin/api/queries'
import { AdminPagination } from '@/features/admin/components/admin-pagination'
import { useDeleteVideoMutation } from '@/shared/api/videos'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Skeleton } from '@/shared/ui/skeleton'
import { VideoStatusBadge } from '@/shared/ui/video-status-badge'

export function AdminVideosTable() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState<PageSize>(10)
  const [filename, setFilename] = useState('')
  const videosQuery = useAdminVideosQuery(page, size, filename)
  const deleteMutation = useDeleteVideoMutation()
  const queryClient = useQueryClient()

  const handleSizeChange = (newSize: PageSize) => {
    setSize(newSize)
    setPage(0)
  }

  const handleFilenameChange = (value: string) => {
    setFilename(value)
    setPage(0)
  }

  const handleDelete = (video: AdminVideo) => {
    if (!confirm(`Excluir o vídeo "${video.originalFilename}"? Essa ação não pode ser desfeita.`)) {
      return
    }
    deleteMutation.mutate(video.id, {
      onSuccess: () => {
        toast.success('Vídeo excluído')
        void queryClient.invalidateQueries({ queryKey: adminKeys.all })
      },
      onError: (error: unknown) => {
        toast.error(error instanceof Error ? error.message : 'Não foi possível excluir o vídeo')
      },
    })
  }

  return (
    <div className="flex flex-col gap-3">
      <Input
        type="search"
        placeholder="Filtrar por nome do vídeo…"
        aria-label="Filtrar vídeos por nome do arquivo"
        className="max-w-xs"
        value={filename}
        onChange={(event) => {
          handleFilenameChange(event.target.value)
        }}
      />

      {videosQuery.isPending && (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      )}

      {videosQuery.isError && (
        <p className="text-sm text-destructive">Não foi possível carregar os vídeos.</p>
      )}

      {videosQuery.data && (
        <>
          <div className="overflow-x-auto border">
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-muted/50">
                <tr>
                  <th className="px-4 py-2 font-medium">Arquivo</th>
                  <th className="px-4 py-2 font-medium">Dono</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2 font-medium">Enviado em</th>
                  <th className="px-4 py-2" />
                </tr>
              </thead>
              <tbody>
                {videosQuery.data.items.map((video) => (
                  <tr key={video.id} className="border-b last:border-b-0">
                    <td className="max-w-48 truncate px-4 py-2">{video.originalFilename}</td>
                    <td className="max-w-48 truncate px-4 py-2 text-muted-foreground">
                      {video.ownerEmail ?? (
                        <span className="font-mono text-xs">{video.userId}</span>
                      )}
                    </td>
                    <td className="px-4 py-2">
                      <VideoStatusBadge status={video.status} />
                    </td>
                    <td className="px-4 py-2 text-muted-foreground">
                      {new Date(video.createdAt).toLocaleString('pt-BR')}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <Button
                        size="icon-sm"
                        variant="outline"
                        disabled={deleteMutation.isPending}
                        onClick={() => {
                          handleDelete(video)
                        }}
                        aria-label={`Excluir ${video.originalFilename}`}
                      >
                        <Trash2 />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {videosQuery.data.items.length === 0 && (
              <p className="px-4 py-6 text-center text-sm text-muted-foreground">
                Nenhum vídeo encontrado.
              </p>
            )}
          </div>

          <AdminPagination
            page={page}
            size={size}
            totalElements={videosQuery.data.totalElements}
            onPageChange={setPage}
            onSizeChange={handleSizeChange}
          />
        </>
      )}
    </div>
  )
}
