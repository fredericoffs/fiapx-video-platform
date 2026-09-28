import { useMutation, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { toast } from 'sonner'
import { PAGE_SIZE, videoKeys, type Video, type VideoPage } from '@/shared/api/videos'
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
      // Só a primeira página recebe o item otimista — é onde ele vai aparecer (mais recente
      // primeiro) até o onSettled invalidar e trazer o dado real.
      queryClient.setQueryData<InfiniteData<VideoPage>>(videoKeys.list(email), (old) => {
        const firstPage = old?.pages[0]
        if (!old || !firstPage) {
          return {
            pages: [{ items: [optimisticVideo], page: 0, size: PAGE_SIZE, totalElements: 1 }],
            pageParams: [0],
          }
        }
        return {
          ...old,
          pages: [
            {
              ...firstPage,
              items: [optimisticVideo, ...firstPage.items],
              totalElements: firstPage.totalElements + 1,
            },
            ...old.pages.slice(1),
          ],
        }
      })

      return { optimisticId: optimisticVideo.id }
    },
    onError: (error, { file }, context) => {
      // Remove só o próprio item otimista: com vários uploads em paralelo, restaurar um
      // snapshot anterior apagaria os itens otimistas dos outros envios ainda em curso.
      if (context) {
        queryClient.setQueryData<InfiniteData<VideoPage>>(videoKeys.list(email), (old) =>
          old
            ? {
                ...old,
                pages: old.pages.map((page) => {
                  const items = page.items.filter((video) => video.id !== context.optimisticId)
                  const removed = page.items.length - items.length
                  return { ...page, items, totalElements: page.totalElements - removed }
                }),
              }
            : old,
        )
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
