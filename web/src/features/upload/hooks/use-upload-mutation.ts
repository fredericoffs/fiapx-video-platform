import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  showsNewUploadsFirst,
  videoKeys,
  type Video,
  type VideoPage,
  type VideoPageParams,
} from '@/shared/api/videos'
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
      // Só as listagens em que um upload novo aparece no topo (1ª página, mais recentes
      // primeiro) recebem o item otimista, até o onSettled invalidar e trazer o dado real.
      for (const [key, old] of queryClient.getQueriesData<VideoPage>({
        queryKey: videoKeys.list(email),
      })) {
        const params = key[3] as VideoPageParams | undefined
        if (!old || !params || !showsNewUploadsFirst(params)) {
          continue
        }
        queryClient.setQueryData<VideoPage>(key, {
          ...old,
          items: [optimisticVideo, ...old.items].slice(0, old.size),
          totalElements: old.totalElements + 1,
        })
      }

      return { optimisticId: optimisticVideo.id }
    },
    onError: (error, { file }, context) => {
      // Remove só o próprio item otimista: com vários uploads em paralelo, restaurar um
      // snapshot anterior apagaria os itens otimistas dos outros envios ainda em curso.
      if (context) {
        queryClient.setQueriesData<VideoPage>({ queryKey: videoKeys.list(email) }, (old) => {
          if (!old) {
            return old
          }
          const items = old.items.filter((video) => video.id !== context.optimisticId)
          return {
            ...old,
            items,
            totalElements: old.totalElements - (old.items.length - items.length),
          }
        })
      }
      const message = error instanceof Error ? error.message : 'Não foi possível enviar o vídeo'
      toast.error(`${file.name}: ${message}`)
    },
    onSuccess: (_data, { file }) => {
      toast.success(`${file.name} enviado — processando`)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: videoKeys.list(email) })
    },
  })
}
