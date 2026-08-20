import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { videoKeys, type Video } from '@/shared/api/videos'
import { uploadVideo } from '@/features/upload/api/upload-video'

interface UploadVariables {
  file: File
  onProgress: (percent: number) => void
}

export function useUploadMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ file, onProgress }: UploadVariables) => uploadVideo(file, onProgress),
    onMutate: async ({ file }) => {
      await queryClient.cancelQueries({ queryKey: videoKeys.list() })
      const previous = queryClient.getQueryData<Video[]>(videoKeys.list())

      const optimisticVideo: Video = {
        id: `optimistic-${crypto.randomUUID()}`,
        originalFilename: file.name,
        status: 'QUEUED',
        errorMessage: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      queryClient.setQueryData<Video[]>(videoKeys.list(), (old) => [
        optimisticVideo,
        ...(old ?? []),
      ])

      return { previous }
    },
    onError: (error, _variables, context) => {
      if (context) {
        queryClient.setQueryData(videoKeys.list(), context.previous)
      }
      toast.error(error instanceof Error ? error.message : 'Não foi possível enviar o vídeo')
    },
    onSuccess: () => {
      toast.success('Vídeo enviado — processando')
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: videoKeys.list() })
    },
  })
}
