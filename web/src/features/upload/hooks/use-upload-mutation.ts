import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { videoKeys, type Video } from '@/shared/api/videos'
import { uploadVideo } from '@/features/upload/api/upload-video'
import { randomId } from '@/shared/lib/random-id'
import { useSessionStore } from '@/shared/lib/session-store'

interface UploadVariables {
  file: File
  onProgress: (percent: number) => void
}

export function useUploadMutation() {
  const queryClient = useQueryClient()
  const email = useSessionStore((state) => state.session?.email)

  return useMutation({
    mutationFn: ({ file, onProgress }: UploadVariables) => uploadVideo(file, onProgress),
    onMutate: async ({ file }) => {
      await queryClient.cancelQueries({ queryKey: videoKeys.list(email) })
      const previous = queryClient.getQueryData<Video[]>(videoKeys.list(email))

      const optimisticVideo: Video = {
        id: `optimistic-${randomId()}`,
        userId: '',
        originalFilename: file.name,
        fileSizeBytes: file.size,
        status: 'QUEUED',
        errorMessage: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      queryClient.setQueryData<Video[]>(videoKeys.list(email), (old) => [
        optimisticVideo,
        ...(old ?? []),
      ])

      return { previous }
    },
    onError: (error, _variables, context) => {
      if (context) {
        queryClient.setQueryData(videoKeys.list(email), context.previous)
      }
      toast.error(error instanceof Error ? error.message : 'Não foi possível enviar o vídeo')
    },
    onSuccess: () => {
      toast.success('Vídeo enviado — processando')
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: videoKeys.list(email) })
    },
  })
}
