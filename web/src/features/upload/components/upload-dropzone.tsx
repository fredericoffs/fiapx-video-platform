import { useEffect, useRef, useState, type DragEvent } from 'react'
import { toast } from 'sonner'
import { UploadCloud } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { Progress } from '@/shared/ui/progress'
import {
  ALLOWED_VIDEO_EXTENSIONS,
  MAX_VIDEO_FILE_SIZE_MB,
  videoFileSchema,
} from '@/features/upload/lib/schemas'
import { useUploadMutation } from '@/features/upload/hooks/use-upload-mutation'

export function UploadDropzone({
  onUploadingChange,
}: {
  onUploadingChange?: (isUploading: boolean) => void
}) {
  const [isDragging, setIsDragging] = useState(false)
  const [progress, setProgress] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const uploadMutation = useUploadMutation()

  useEffect(() => {
    onUploadingChange?.(uploadMutation.isPending)
  }, [uploadMutation.isPending, onUploadingChange])

  const handleFile = (file: File) => {
    const result = videoFileSchema.safeParse(file)
    if (!result.success) {
      toast.error(result.error.issues[0]?.message ?? 'Arquivo inválido')
      return
    }
    setProgress(0)
    uploadMutation.mutate({ file, onProgress: setProgress })
  }

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    setIsDragging(false)
    const file = event.dataTransfer.files[0]
    if (file) {
      handleFile(file)
    }
  }

  return (
    <div>
      <div
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            inputRef.current?.click()
          }
        }}
        onDragOver={(event) => {
          event.preventDefault()
          setIsDragging(true)
        }}
        onDragLeave={() => {
          setIsDragging(false)
        }}
        onDrop={handleDrop}
        className={cn(
          'flex cursor-pointer flex-col items-center justify-center gap-2 border-2 border-dashed px-6 py-10 text-center transition-colors',
          isDragging ? 'border-primary bg-accent' : 'border-border hover:bg-muted/50',
        )}
      >
        <UploadCloud className="size-8 text-muted-foreground" aria-hidden />
        <p className="text-sm">
          Arraste um vídeo aqui ou{' '}
          <span className="text-primary underline">escolha um arquivo</span>
        </p>
        <p className="text-xs text-muted-foreground">
          Formatos aceitos: {ALLOWED_VIDEO_EXTENSIONS.join(', ')} — até {MAX_VIDEO_FILE_SIZE_MB}MB
        </p>
        <input
          ref={inputRef}
          type="file"
          accept={ALLOWED_VIDEO_EXTENSIONS.map((extension) => `.${extension}`).join(',')}
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) {
              handleFile(file)
            }
            event.target.value = ''
          }}
        />
      </div>
      {uploadMutation.isPending && (
        <div className="mt-3 flex items-center gap-3">
          <Progress value={progress} className="flex-1" />
          <span className="font-heading text-xs text-muted-foreground">{progress}%</span>
        </div>
      )}
    </div>
  )
}
