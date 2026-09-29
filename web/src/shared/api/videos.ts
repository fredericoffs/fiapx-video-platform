import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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

export type VideoSortField = 'CREATED_AT' | 'FILENAME' | 'FILE_SIZE'
export type SortDirection = 'ASC' | 'DESC'

/** Datas no formato do `<input type="datetime-local">` (hora local); '' = sem filtro. */
export interface VideoListFilters {
  createdFrom: string
  createdTo: string
  sortBy: VideoSortField
  direction: SortDirection
}

export const DEFAULT_VIDEO_LIST_FILTERS: VideoListFilters = {
  createdFrom: '',
  createdTo: '',
  sortBy: 'CREATED_AT',
  direction: 'DESC',
}

export interface VideoPageParams {
  page: number
  filters: VideoListFilters
}

const MINUTE_MS = 60_000

/**
 * Converte os filtros da tela pros parâmetros da API. O datetime-local tem precisão de
 * minuto, então o "até" cobre o minuto inteiro (15:00 inclui 15:00:59).
 */
export function toVideoListQuery(filters: VideoListFilters) {
  return {
    ...(filters.createdFrom ? { createdFrom: new Date(filters.createdFrom).toISOString() } : {}),
    ...(filters.createdTo
      ? { createdTo: new Date(new Date(filters.createdTo).getTime() + MINUTE_MS - 1).toISOString() }
      : {}),
    sortBy: filters.sortBy,
    direction: filters.direction,
  }
}

export function hasActiveDateFilter(filters: VideoListFilters): boolean {
  return filters.createdFrom !== '' || filters.createdTo !== ''
}

/** Só a 1ª página em "mais recentes primeiro" sem teto de data mostra um upload recém-feito no topo. */
export function showsNewUploadsFirst({ page, filters }: VideoPageParams): boolean {
  return (
    page === 0 &&
    filters.sortBy === 'CREATED_AT' &&
    filters.direction === 'DESC' &&
    filters.createdTo === ''
  )
}

// Escopado por usuário (e-mail da sessão): sem isso, o cache de uma conta apareceria pra
// outra que logasse na mesma aba antes de os dados serem revalidados (ou numa falha de rede).
export const videoKeys = {
  all: ['videos'] as const,
  list: (email: string | undefined) => [...videoKeys.all, 'list', email] as const,
  page: (email: string | undefined, params: VideoPageParams) =>
    [...videoKeys.list(email), params] as const,
}

const NON_TERMINAL_STATUSES: VideoStatus[] = ['QUEUED', 'PROCESSING']

export function hasNonTerminalVideo(videos: Video[] | undefined): boolean {
  return (videos ?? []).some((video) => NON_TERMINAL_STATUSES.includes(video.status))
}

export const PAGE_SIZE = 10
// Seis consultas/min: a cota de /videos no gateway é 20/min (escopada por rota — ver
// RateLimitFilterFunction), e sobra folga pra troca de página e de filtro competirem com o polling.
const POLL_INTERVAL_MS = 10000

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

// Paginado por número de página (PAGE_SIZE por vez), com filtro e ordenação no servidor —
// ordenar só o que já veio do servidor misturaria a ordem entre páginas.
export function useVideosQuery(page: number, filters: VideoListFilters) {
  const email = useSessionStore((state) => state.session?.email)
  return useQuery({
    queryKey: videoKeys.page(email, { page, filters }),
    queryFn: async (): Promise<VideoPage> => {
      const { data, response } = await apiClient.GET('/videos', {
        params: { query: { page, size: PAGE_SIZE, ...toVideoListQuery(filters) } },
      })
      if (response.status === 429) {
        throw new RateLimitedError(parseRetryAfterMs(response))
      }
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar seus vídeos')
      }
      return {
        items: (data.items ?? []).map(toVideo).filter((video): video is Video => video !== null),
        page: data.page ?? page,
        size: data.size ?? PAGE_SIZE,
        totalElements: data.totalElements ?? 0,
      }
    },
    enabled: email !== undefined,
    // Mantém a página anterior na tela enquanto a próxima carrega, em vez de piscar o skeleton.
    placeholderData: keepPreviousData,
    // Backoff próprio via Retry-After no lugar do retry padrão do TanStack (que não olha o
    // header e tentaria de novo antes do servidor liberar a cota de novo).
    retry: false,
    refetchInterval: (query) => {
      if (query.state.error instanceof RateLimitedError) {
        return query.state.error.retryAfterMs
      }
      return hasNonTerminalVideo(query.state.data?.items) ? POLL_INTERVAL_MS : false
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
