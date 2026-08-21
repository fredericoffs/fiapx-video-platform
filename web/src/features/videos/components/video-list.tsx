import { useState } from 'react'
import { toast } from 'sonner'
import { Download, Loader2 } from 'lucide-react'
import { useVideosQuery } from '@/shared/api/videos'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { VideoProcessingIllustration } from '@/shared/ui/illustrations/video-processing-illustration'
import { VideoStatusBadge } from '@/features/videos/components/video-status-badge'
import { downloadVideo } from '@/features/videos/api/download-video'

export function VideoList() {
  const videosQuery = useVideosQuery()
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

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

  return (
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
          <div className="flex shrink-0 items-center gap-3">
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
          </div>
        </li>
      ))}
    </ul>
  )
}
