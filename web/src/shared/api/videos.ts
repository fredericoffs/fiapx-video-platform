import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'

export type VideoStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface Video {
  id: string
  originalFilename: string
  status: VideoStatus
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

function toVideo(dto: components['schemas']['VideoStatusResponse']): Video | null {
  if (!dto.id) {
    return null
  }
  return {
    id: dto.id,
    originalFilename: dto.originalFilename ?? '',
    status: dto.status ?? 'QUEUED',
    errorMessage: dto.errorMessage ?? null,
    createdAt: dto.createdAt ?? new Date().toISOString(),
    updatedAt: dto.updatedAt ?? new Date().toISOString(),
  }
}

export const videoKeys = {
  all: ['videos'] as const,
  list: () => [...videoKeys.all, 'list'] as const,
}

const NON_TERMINAL_STATUSES: VideoStatus[] = ['QUEUED', 'PROCESSING']

export function useVideosQuery() {
  return useQuery({
    queryKey: videoKeys.list(),
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/videos')
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar seus vídeos')
      }
      return (data.items ?? []).map(toVideo).filter((video): video is Video => video !== null)
    },
    refetchInterval: (query) => {
      const videos = query.state.data
      const hasPending = videos?.some((video) => NON_TERMINAL_STATUSES.includes(video.status))
      return hasPending ? 3000 : false
    },
  })
}
