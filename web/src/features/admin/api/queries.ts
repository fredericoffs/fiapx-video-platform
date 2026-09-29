import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import { toVideoListQuery, type VideoListFilters, type VideoStatus } from '@/shared/api/videos'
import type { components } from '@/shared/api/schema.gen'
import type { Role } from '@/shared/lib/session-store'

export interface AdminUser {
  id: string
  email: string
  role: Role
  createdAt: string
}

export interface AdminVideo {
  id: string
  userId: string
  ownerEmail: string | null
  originalFilename: string
  fileSizeBytes: number | null
  status: VideoStatus
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
}

export type PageSize = 10 | 20
export const PAGE_SIZES: readonly PageSize[] = [10, 20]

function toAdminUser(dto: components['schemas']['AdminUserResponse']): AdminUser | null {
  if (!dto.id) {
    return null
  }
  return {
    id: dto.id,
    email: dto.email ?? '',
    role: dto.role ?? 'USER',
    createdAt: dto.createdAt ?? new Date().toISOString(),
  }
}

function toAdminVideo(dto: components['schemas']['AdminVideoResponse']): AdminVideo | null {
  if (!dto.id) {
    return null
  }
  return {
    id: dto.id,
    userId: dto.userId ?? '',
    ownerEmail: dto.ownerEmail ?? null,
    originalFilename: dto.originalFilename ?? '',
    fileSizeBytes: dto.fileSizeBytes ?? null,
    status: dto.status ?? 'QUEUED',
    errorMessage: dto.errorMessage ?? null,
    createdAt: dto.createdAt ?? new Date().toISOString(),
    updatedAt: dto.updatedAt ?? new Date().toISOString(),
  }
}

export const adminKeys = {
  all: ['admin'] as const,
  users: (page: number, size: number, email: string) =>
    [...adminKeys.all, 'users', page, size, email] as const,
  videos: (page: number, size: number, filename: string, filters: VideoListFilters) =>
    [...adminKeys.all, 'videos', page, size, filename, filters] as const,
}

export function useAdminUsersQuery(page: number, size: PageSize, email = '') {
  return useQuery({
    queryKey: adminKeys.users(page, size, email),
    queryFn: async (): Promise<Page<AdminUser>> => {
      const { data, response } = await apiClient.GET('/admin/users', {
        params: { query: email ? { page, size, email } : { page, size } },
      })
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar os usuários')
      }
      return {
        items: (data.items ?? [])
          .map(toAdminUser)
          .filter((user): user is AdminUser => user !== null),
        page: data.page ?? page,
        size: data.size ?? size,
        totalElements: data.totalElements ?? 0,
      }
    },
  })
}

export function useAdminVideosQuery(
  page: number,
  size: PageSize,
  filename: string,
  filters: VideoListFilters,
) {
  return useQuery({
    queryKey: adminKeys.videos(page, size, filename, filters),
    queryFn: async (): Promise<Page<AdminVideo>> => {
      const { data, response } = await apiClient.GET('/admin/videos', {
        params: {
          query: {
            page,
            size,
            ...(filename ? { filename } : {}),
            ...toVideoListQuery(filters),
          },
        },
      })
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar os vídeos')
      }
      return {
        items: (data.items ?? [])
          .map(toAdminVideo)
          .filter((video): video is AdminVideo => video !== null),
        page: data.page ?? page,
        size: data.size ?? size,
        totalElements: data.totalElements ?? 0,
      }
    },
    placeholderData: keepPreviousData,
  })
}

export function useDeleteUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (userId: string) => {
      const { response } = await apiClient.DELETE('/admin/users/{id}', {
        params: { path: { id: userId } },
      })
      if (!response.ok) {
        throw new Error('Não foi possível excluir o usuário')
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all })
    },
  })
}
