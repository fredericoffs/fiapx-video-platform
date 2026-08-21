import { useState } from 'react'
import { createFileRoute } from '@tanstack/react-router'
import { hasNonTerminalVideo, useVideosQuery } from '@/shared/api/videos'
import { UploadProcessingScene } from '@/shared/ui/illustrations/upload-processing-scene'
import { UploadDropzone } from '@/features/upload/components/upload-dropzone'
import { VideoList } from '@/features/videos/components/video-list'

export const Route = createFileRoute('/_authenticated/')({
  component: DashboardPage,
})

function DashboardPage() {
  const [isUploading, setIsUploading] = useState(false)
  const videosQuery = useVideosQuery()
  const isProcessing = hasNonTerminalVideo(videosQuery.data)
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
      <VideoList />
    </div>
  )
}
