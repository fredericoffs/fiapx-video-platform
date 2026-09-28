import { useEffect, useRef, useState, type DragEvent } from 'react'
import { toast } from 'sonner'
import { UploadCloud } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { randomId } from '@/shared/lib/random-id'
import { Progress } from '@/shared/ui/progress'
import {
  ALLOWED_VIDEO_EXTENSIONS,
  MAX_VIDEO_FILE_SIZE_MB,
  videoFileSchema,
} from '@/features/upload/lib/schemas'
import { useUploadMutation } from '@/features/upload/hooks/use-upload-mutation'

interface UploadInProgress {
  id: string
  name: string
  progress: number
}

export function UploadDropzone({
  onUploadingChange,
}: {
  onUploadingChange?: (isUploading: boolean) => void
}) {
  const [isDragging, setIsDragging] = useState(false)
  // Um item por arquivo em envio: o estado do useMutation só reflete a última chamada, então
  // progresso e "enviando" de vários uploads em paralelo ficam aqui.
  const [uploads, setUploads] = useState<UploadInProgress[]>([])
  const inputRef = useRef<HTMLInputElement>(null)
  const uploadMutation = useUploadMutation()
  const isUploading = uploads.length > 0

  useEffect(() => {
    onUploadingChange?.(isUploading)
  }, [isUploading, onUploadingChange])

  const updateProgress = (id: string, progress: number) => {
    setUploads((current) =>
      current.map((upload) => (upload.id === id ? { ...upload, progress } : upload)),
    )
  }

  const handleFile = (file: File) => {
    const result = videoFileSchema.safeParse(file)
    if (!result.success) {
      toast.error(`${file.name}: ${result.error.issues[0]?.message ?? 'Arquivo inválido'}`)
      return
    }
    const id = randomId()
    setUploads((current) => [...current, { id, name: file.name, progress: 0 }])
    uploadMutation
      .mutateAsync({
        file,
        onProgress: (percent) => {
          updateProgress(id, percent)
        },
      })
      // O erro já vira toast no onError do hook; aqui só não deixa a promise rejeitada solta.
      .catch(() => undefined)
      .finally(() => {
        setUploads((current) => current.filter((upload) => upload.id !== id))
      })
  }

  const handleFiles = (files: FileList | null) => {
    Array.from(files ?? []).forEach(handleFile)
  }

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    setIsDragging(false)
    handleFiles(event.dataTransfer.files)
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
          Arraste um ou mais vídeos aqui ou{' '}
          <span className="text-primary underline">escolha os arquivos</span>
        </p>
        <p className="text-xs text-muted-foreground">
          Formatos aceitos: {ALLOWED_VIDEO_EXTENSIONS.join(', ')} — até {MAX_VIDEO_FILE_SIZE_MB}MB
          por arquivo
        </p>
        <input
          ref={inputRef}
          type="file"
          multiple
          data-testid="upload-input"
          accept={ALLOWED_VIDEO_EXTENSIONS.map((extension) => `.${extension}`).join(',')}
          className="hidden"
          onChange={(event) => {
            handleFiles(event.target.files)
            event.target.value = ''
          }}
        />
      </div>
      {isUploading && (
        <ul className="mt-3 flex flex-col gap-2">
          {uploads.map((upload) => (
            <li key={upload.id} className="flex items-center gap-3">
              <span className="w-40 truncate text-xs" title={upload.name}>
                {upload.name}
              </span>
              <Progress value={upload.progress} className="flex-1" aria-label={upload.name} />
              <span className="font-heading text-xs text-muted-foreground">{upload.progress}%</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
