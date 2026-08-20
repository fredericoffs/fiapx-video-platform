import { createFileRoute } from '@tanstack/react-router'
import { UploadDropzone } from '@/features/upload/components/upload-dropzone'

export const Route = createFileRoute('/_authenticated/')({
  component: DashboardPage,
})

function DashboardPage() {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="font-heading text-2xl font-semibold">Meus vídeos</h1>
      <UploadDropzone />
    </div>
  )
}
