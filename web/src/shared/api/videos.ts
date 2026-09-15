import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'
import { useSessionStore } from '@/shared/lib/session-store'

export type VideoStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface Video {
  id: string
  userId: string
  originalFilename: string
  fileSizeBytes: number | null
  status: VideoStatus
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export function toVideo(dto: components['schemas']['VideoStatusResponse']): Video | null {
  if (!dto.id) {
    return null
  }
  return {
    id: dto.id,
    userId: dto.userId ?? '',
    originalFilename: dto.originalFilename ?? '',
    fileSizeBytes: dto.fileSizeBytes ?? null,
    status: dto.status ?? 'QUEUED',
    errorMessage: dto.errorMessage ?? null,
    createdAt: dto.createdAt ?? new Date().toISOString(),
    updatedAt: dto.updatedAt ?? new Date().toISOString(),
  }
}

// Escopado por usuário (e-mail da sessão): sem isso, o cache de uma conta apareceria pra
// outra que logasse na mesma aba antes de os dados serem revalidados (ou numa falha de rede).
export const videoKeys = {
  all: ['videos'] as const,
  list: (email: string | undefined) => [...videoKeys.all, 'list', email] as const,
}

const NON_TERMINAL_STATUSES: VideoStatus[] = ['QUEUED', 'PROCESSING']

export function hasNonTerminalVideo(videos: Video[] | undefined): boolean {
  return (videos ?? []).some((video) => NON_TERMINAL_STATUSES.includes(video.status))
}

export function useVideosQuery() {
  const email = useSessionStore((state) => state.session?.email)
  return useQuery({
    queryKey: videoKeys.list(email),
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/videos')
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar seus vídeos')
      }
      return (data.items ?? []).map(toVideo).filter((video): video is Video => video !== null)
    },
    enabled: email !== undefined,
    refetchInterval: (query) => (hasNonTerminalVideo(query.state.data) ? 3000 : false),
  })
}

export function useDeleteVideoMutation() {
  const queryClient = useQueryClient()
  const email = useSessionStore((state) => state.session?.email)
  return useMutation({
    mutationFn: async (videoId: string) => {
      const { response } = await apiClient.DELETE('/videos/{id}', {
        params: { path: { id: videoId } },
      })
      if (!response.ok) {
        throw new Error('Não foi possível excluir o vídeo')
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: videoKeys.list(email) })
    },
  })
}
