import { afterEach, describe, expect, it, vi } from 'vitest'

function jsonResponse(status: number, body: unknown = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function loggedInSession() {
  return {
    token: 'old-token',
    email: 'user@example.com',
    role: 'USER' as const,
    mustChangePassword: false,
  }
}

// createClient() captura o fetch global na hora que o módulo é importado — pra um mock de
// fetch valer, o stub precisa existir ANTES do import, daí o reset + import dinâmico por
// teste. session-store precisa ser reimportado junto (mesmo reset de módulos), senão as
// asserções leriam uma instância da store diferente da que o client novo enxerga.
async function freshApiClient(fetchMock: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('fetch', fetchMock)
  vi.resetModules()
  const { useSessionStore } = await import('@/shared/lib/session-store')
  const { apiClient } = await import('@/shared/api/client')
  useSessionStore.setState({ session: loggedInSession() })
  return { apiClient, useSessionStore }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.resetModules()
})

describe('apiClient', () => {
  // Item 14: qualquer rota que não seja login/change-password respondendo 401 só pode
  // significar token expirado ou revogado — a sessão precisa cair pra forçar o re-login.
  it('limpa a sessão quando uma rota comum responde 401', async () => {
    const { apiClient, useSessionStore } = await freshApiClient(
      vi.fn().mockResolvedValue(jsonResponse(401)),
    )

    await apiClient.GET('/videos')

    expect(useSessionStore.getState().session).toBeNull()
  })

  // /auth/login e /users/me/password têm motivo próprio pra 401 (credenciais erradas) que
  // não tem nada a ver com a sessão atual estar válida ou não — não pode deslogar por isso.
  it('não mexe na sessão quando /auth/login responde 401 (credenciais erradas)', async () => {
    const { apiClient, useSessionStore } = await freshApiClient(
      vi.fn().mockResolvedValue(jsonResponse(401)),
    )

    await apiClient.POST('/auth/login', { body: { email: 'a@b.com', password: 'errada' } })

    expect(useSessionStore.getState().session).not.toBeNull()
  })

  it('não mexe na sessão quando /users/me/password responde 401 (senha atual errada)', async () => {
    const { apiClient, useSessionStore } = await freshApiClient(
      vi.fn().mockResolvedValue(jsonResponse(401, { title: 'Senha atual inválida' })),
    )

    await apiClient.PUT('/users/me/password', {
      body: { currentPassword: 'errada', newPassword: 'nova-senha-123' },
    })

    expect(useSessionStore.getState().session).not.toBeNull()
  })

  it('expira a sessão quando o token falha durante a troca de senha', async () => {
    const { apiClient, useSessionStore } = await freshApiClient(
      vi.fn().mockResolvedValue(jsonResponse(401)),
    )
    await apiClient.PUT('/users/me/password', {
      body: { currentPassword: 'atual', newPassword: 'nova-senha-123' },
    })
    expect(useSessionStore.getState().session).toBeNull()
  })

  it('não encerra uma sessão nova por uma resposta atrasada da sessão antiga', async () => {
    let complete!: (response: Response) => void
    const fetchMock = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          complete = resolve
        }),
    )
    const { apiClient, useSessionStore } = await freshApiClient(fetchMock)
    const request = apiClient.GET('/videos')
    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalled()
    })
    useSessionStore.setState({ session: { ...loggedInSession(), token: 'new-token' } })
    complete(jsonResponse(401))
    await request
    expect(useSessionStore.getState().session?.token).toBe('new-token')
  })

  it('não faz nada quando a resposta não é 401', async () => {
    const { apiClient, useSessionStore } = await freshApiClient(
      vi
        .fn()
        .mockResolvedValue(jsonResponse(200, { items: [], page: 0, size: 20, totalElements: 0 })),
    )

    await apiClient.GET('/videos')

    expect(useSessionStore.getState().session).not.toBeNull()
  })
})
