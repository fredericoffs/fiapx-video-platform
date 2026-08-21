import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import { toVideo, type Video } from '@/shared/api/videos'
import type { components } from '@/shared/api/schema.gen'
import type { Role } from '@/shared/lib/session-store'

export interface AdminUser {
  id: string
  email: string
  role: Role
  createdAt: string
}

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

export const adminKeys = {
  all: ['admin'] as const,
  users: () => [...adminKeys.all, 'users'] as const,
  videos: () => [...adminKeys.all, 'videos'] as const,
}

export function useAdminUsersQuery() {
  return useQuery({
    queryKey: adminKeys.users(),
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/admin/users', {
        params: { query: { size: 100 } },
      })
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar os usuários')
      }
      return (data.items ?? []).map(toAdminUser).filter((user): user is AdminUser => user !== null)
    },
  })
}

export function useAdminVideosQuery() {
  return useQuery({
    queryKey: adminKeys.videos(),
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/admin/videos', {
        params: { query: { size: 100 } },
      })
      if (!response.ok || !data) {
        throw new Error('Não foi possível carregar os vídeos')
      }
      return (data.items ?? []).map(toVideo).filter((video): video is Video => video !== null)
    },
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
      void queryClient.invalidateQueries({ queryKey: adminKeys.users() })
      void queryClient.invalidateQueries({ queryKey: adminKeys.videos() })
    },
  })
}
