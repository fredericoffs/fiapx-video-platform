import { useState } from 'react'
import { toast } from 'sonner'
import { Download, LayoutGrid, List, Loader2, Trash2 } from 'lucide-react'
import { useDeleteVideoMutation, useVideosQuery, type Video } from '@/shared/api/videos'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Skeleton } from '@/shared/ui/skeleton'
import { VideoProcessingIllustration } from '@/shared/ui/illustrations/video-processing-illustration'
import { VideoStatusBadge } from '@/features/videos/components/video-status-badge'
import { downloadVideo } from '@/features/videos/api/download-video'

type ViewMode = 'list' | 'cards'

export function VideoList() {
  const videosQuery = useVideosQuery()
  const deleteMutation = useDeleteVideoMutation()
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [viewMode, setViewMode] = useState<ViewMode>('list')

  const handleDownload = (id: string, originalFilename: string) => {
    setDownloadingId(id)
    downloadVideo(id, originalFilename)
      .catch((error: unknown) => {
        toast.error(error instanceof Error ? error.message : 'Não foi possível baixar o vídeo')
      })
      .finally(() => {
        setDownloadingId(null)
      })
  }

  const handleDelete = (video: Video) => {
    if (!confirm(`Excluir o vídeo "${video.originalFilename}"? Essa ação não pode ser desfeita.`)) {
      return
    }
    deleteMutation.mutate(video.id, {
      onSuccess: () => {
        toast.success('Vídeo excluído')
      },
      onError: (error: unknown) => {
        toast.error(error instanceof Error ? error.message : 'Não foi possível excluir o vídeo')
      },
    })
  }

  if (videosQuery.isPending) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-14 w-full" />
        <Skeleton className="h-14 w-full" />
        <Skeleton className="h-14 w-full" />
      </div>
    )
  }

  if (videosQuery.isError) {
    return <p className="text-sm text-destructive">Não foi possível carregar seus vídeos.</p>
  }

  if (videosQuery.data.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 py-6 text-center">
        <VideoProcessingIllustration className="w-48" />
        <p className="text-sm text-muted-foreground">Nenhum vídeo enviado ainda.</p>
      </div>
    )
  }

  const actions = (video: Video) => (
    <div className="flex shrink-0 items-center gap-2">
      <VideoStatusBadge status={video.status} />
      {video.status === 'COMPLETED' && (
        <Button
          size="icon"
          variant="outline"
          disabled={downloadingId === video.id}
          onClick={() => {
            handleDownload(video.id, video.originalFilename)
          }}
          aria-label="Baixar zip de frames"
        >
          {downloadingId === video.id ? <Loader2 className="animate-spin" /> : <Download />}
        </Button>
      )}
      <Button
        size="icon"
        variant="outline"
        disabled={deleteMutation.isPending}
        onClick={() => {
          handleDelete(video)
        }}
        aria-label="Excluir vídeo"
      >
        <Trash2 />
      </Button>
    </div>
  )

  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end gap-1">
        <Button
          size="icon-sm"
          variant={viewMode === 'list' ? 'secondary' : 'ghost'}
          onClick={() => {
            setViewMode('list')
          }}
          aria-label="Ver como lista"
          aria-pressed={viewMode === 'list'}
        >
          <List />
        </Button>
        <Button
          size="icon-sm"
          variant={viewMode === 'cards' ? 'secondary' : 'ghost'}
          onClick={() => {
            setViewMode('cards')
          }}
          aria-label="Ver como cartões"
          aria-pressed={viewMode === 'cards'}
        >
          <LayoutGrid />
        </Button>
      </div>

      {viewMode === 'list' ? (
        <ul className="flex flex-col gap-2">
          {videosQuery.data.map((video) => (
            <li key={video.id} className="flex items-center justify-between gap-3 border px-4 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{video.originalFilename}</p>
                <p className="text-xs text-muted-foreground">
                  {new Date(video.createdAt).toLocaleString('pt-BR')}
                </p>
                {video.status === 'FAILED' && video.errorMessage && (
                  <p className="text-xs text-destructive">{video.errorMessage}</p>
                )}
              </div>
              {actions(video)}
            </li>
          ))}
        </ul>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {videosQuery.data.map((video) => (
            <Card key={video.id} className="gap-3 p-4">
              <p className="truncate text-sm font-medium">{video.originalFilename}</p>
              <p className="text-xs text-muted-foreground">
                {new Date(video.createdAt).toLocaleString('pt-BR')}
              </p>
              {video.status === 'FAILED' && video.errorMessage && (
                <p className="text-xs text-destructive">{video.errorMessage}</p>
              )}
              <div className="mt-1">{actions(video)}</div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
