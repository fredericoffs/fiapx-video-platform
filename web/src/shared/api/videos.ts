import {
  type InfiniteData,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'
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

export interface VideoPage {
  items: Video[]
  page: number
  size: number
  totalElements: number
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

export function flattenVideoPages(data: InfiniteData<VideoPage> | undefined): Video[] {
  return data?.pages.flatMap((videoPage) => videoPage.items) ?? []
}

export const PAGE_SIZE = 20
// >20/min (a cota de /videos no gateway, escopada por rota — ver RateLimitFilterFunction)
// sobraria zero folga pra "carregar mais" ou um refresh manual competirem com o polling.
const POLL_INTERVAL_MS = 10000

export function pollingIntervalForPages(pages: number): number {
  return POLL_INTERVAL_MS * Math.max(1, pages)
}

const DEFAULT_RETRY_AFTER_MS = 5000

/** 429 do gateway (rate limit por rota): o backoff usa o Retry-After do servidor, não um valor fixo. */
export class RateLimitedError extends Error {
  retryAfterMs: number

  constructor(retryAfterMs: number) {
    super('Muitas requisições — aguardando antes de tentar de novo')
    this.name = 'RateLimitedError'
    this.retryAfterMs = retryAfterMs
  }
}

function parseRetryAfterMs(response: Response): number {
  const header = response.headers.get('Retry-After')
  const seconds = header ? Number(header) : Number.NaN
  return Number.isFinite(seconds) && seconds >= 0 ? seconds * 1000 : DEFAULT_RETRY_AFTER_MS
}

// Paginado (useInfiniteQuery): sem isso, só os PAGE_SIZE vídeos mais recentes eram alcançáveis
// e vídeos mais antigos somiam da listagem sem nenhuma forma de chegar até eles.
export function useVideosQuery() {
  const email = useSessionStore((state) => state.session?.email)
  return useInfiniteQuery({
    queryKey: videoKeys.list(email),
    queryFn: async ({ pageParam }): Promise<VideoPage> => {
      const { data, response } = await apiClient.GET('/videos', {
        params: { query: { page: pageParam, size: PAGE_SIZE } },
      })
      if (response.status === 429) {
        throw new RateLimitedError(parseRetryAfterMs(response))
      }
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar seus vídeos')
      }
      return {
        items: (data.items ?? []).map(toVideo).filter((video): video is Video => video !== null),
        page: data.page ?? pageParam,
        size: data.size ?? PAGE_SIZE,
        totalElements: data.totalElements ?? 0,
      }
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loadedSoFar = (lastPage.page + 1) * lastPage.size
      return loadedSoFar < lastPage.totalElements ? lastPage.page + 1 : undefined
    },
    enabled: email !== undefined,
    // Backoff próprio via Retry-After no lugar do retry padrão do TanStack (que não olha o
    // header e tentaria de novo antes do servidor liberar a cota de novo).
    retry: false,
    refetchInterval: (query) => {
      if (query.state.error instanceof RateLimitedError) {
        return query.state.error.retryAfterMs
      }
      return hasNonTerminalVideo(flattenVideoPages(query.state.data))
        ? pollingIntervalForPages(query.state.data?.pages.length ?? 1)
        : false
    },
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
