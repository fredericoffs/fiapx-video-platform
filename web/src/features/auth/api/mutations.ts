import { useMutation } from '@tanstack/react-query'
import { useSessionStore } from '@/shared/lib/session-store'
import { apiClient } from '@/shared/api/client'
import type {
  ChangePasswordFormValues,
  LoginFormValues,
  RegisterFormValues,
} from '@/features/auth/lib/schemas'

export class AuthApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'AuthApiError'
    this.status = status
  }
}

export function useLoginMutation() {
  return useMutation({
    mutationFn: async (input: LoginFormValues) => {
      const { data, response } = await apiClient.POST('/auth/login', { body: input })
      if (!response.ok || !data?.accessToken) {
        if (response.status === 401) {
          throw new AuthApiError('E-mail ou senha inválidos', response.status)
        }
        if (response.status === 429) {
          throw new AuthApiError(
            'Muitas tentativas — aguarde um pouco antes de tentar de novo',
            response.status,
          )
        }
        throw new AuthApiError('Não foi possível entrar. Tente novamente.', response.status)
      }
      return {
        accessToken: data.accessToken,
        role: data.role ?? 'USER',
        mustChangePassword: data.mustChangePassword ?? false,
      }
    },
  })
}

export function useChangePasswordMutation() {
  return useMutation({
    mutationFn: async (input: ChangePasswordFormValues) => {
      const { data, response } = await apiClient.PUT('/users/me/password', { body: input })
      if (!response.ok || !data?.accessToken) {
        if (response.status === 401) {
          throw new AuthApiError(
            useSessionStore.getState().session
              ? 'Senha atual incorreta'
              : 'Sua sessão expirou. Faça login novamente.',
            response.status,
          )
        }
        throw new AuthApiError('Não foi possível trocar a senha. Tente novamente.', response.status)
      }
      // A troca de senha revoga qualquer token emitido antes dela (item 14) — inclusive o
      // que autenticou esta própria requisição. Sem propagar o token novo pra sessão, a
      // próxima chamada da API usaria um token já revogado e cairia num 401.
      return {
        accessToken: data.accessToken,
        role: data.role ?? 'USER',
        mustChangePassword: data.mustChangePassword ?? false,
      }
    },
  })
}

export function useRegisterMutation() {
  return useMutation({
    mutationFn: async (input: RegisterFormValues) => {
      const { data, response } = await apiClient.POST('/auth/register', { body: input })
      if (!response.ok || !data) {
        if (response.status === 409) {
          throw new AuthApiError('Este e-mail já está cadastrado', response.status)
        }
        throw new AuthApiError('Não foi possível criar a conta. Tente novamente.', response.status)
      }
      return data
    },
  })
}
