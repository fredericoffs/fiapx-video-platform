import { useState } from 'react'
import { createFileRoute, redirect } from '@tanstack/react-router'
import { useSessionStore } from '@/shared/lib/session-store'
import {
  DEFAULT_VIDEO_LIST_FILTERS,
  hasNonTerminalVideo,
  useVideosQuery,
  type VideoListFilters,
} from '@/shared/api/videos'
import { UploadProcessingScene } from '@/shared/ui/illustrations/upload-processing-scene'
import { UploadDropzone } from '@/features/upload/components/upload-dropzone'
import { VideoList } from '@/features/videos/components/video-list'

export const Route = createFileRoute('/_authenticated/')({
  beforeLoad: () => {
    const session = useSessionStore.getState().session
    if (session?.role === 'ADMIN') {
      redirect({ to: '/admin', throw: true })
    }
  },
  component: DashboardPage,
})

function DashboardPage() {
  const [isUploading, setIsUploading] = useState(false)
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<VideoListFilters>(DEFAULT_VIDEO_LIST_FILTERS)
  // Mesma chave da VideoList: o TanStack compartilha a consulta, sem requisição duplicada.
  const videosQuery = useVideosQuery(page, filters)
  const isProcessing = hasNonTerminalVideo(videosQuery.data?.items)
  const sceneState = isUploading ? 'uploading' : isProcessing ? 'processing' : 'idle'

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div>
        <h1 className="font-heading text-2xl font-semibold">Meus vídeos</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Envie um vídeo, acompanhe o processamento em tempo real e baixe os frames extraídos quando
          o status virar “Concluído”.
        </p>
      </div>
      <UploadProcessingScene state={sceneState} className="w-full" />
      <UploadDropzone onUploadingChange={setIsUploading} />
      <VideoList
        page={page}
        filters={filters}
        onPageChange={setPage}
        onFiltersChange={(newFilters) => {
          setFilters(newFilters)
          setPage(0)
        }}
      />
    </div>
  )
}
