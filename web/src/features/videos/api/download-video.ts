import { expireSession } from '@/shared/lib/expire-session'
import { baseUrl } from '@/shared/api/client'
import { useSessionStore } from '@/shared/lib/session-store'

function filenameFromDisposition(header: string | null, fallback: string): string {
  const match = header ? /filename="?([^"]+)"?/.exec(header) : null
  return match?.[1] ?? fallback
}

/**
 * Faço um download autenticado: o endpoint exige Authorization: Bearer, então não
 * dá pra usar um <a href> puro — preciso buscar via fetch e disparar o download.
 *
 * Item 15 da revisão crítica: pro maior vídeo permitido, segurar o zip inteiro como Blob
 * na memória da aba antes de salvar é o pior caso de uso de memória do fluxo de download.
 * Onde a File System Access API existe (Chromium), gravo direto no disco enquanto o corpo
 * da resposta chega (response.body.pipeTo), sem acumular nada em memória. Firefox/Safari
 * não implementam essa API — caem no download via Blob de sempre (mesmo comportamento de
 * antes desta mudança, nenhuma regressão pra quem já funcionava assim).
 */
export async function downloadVideo(videoId: string, originalFilename: string): Promise<void> {
  const token = useSessionStore.getState().session?.token
  const headers = new Headers()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  // O diálogo precisa abrir no gesto do clique, antes de qualquer espera de rede.
  let handle: SaveFileHandle | undefined
  if (typeof window.showSaveFilePicker === 'function') {
    try {
      handle = await window.showSaveFilePicker({ suggestedName: `${originalFilename}.zip` })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      throw error
    }
  }
  const response = await fetch(`${baseUrl}/videos/${videoId}/download`, { headers })
  if (response.status === 401) expireSession(token)
  if (!response.ok || !response.body) {
    await response.body?.cancel()
    throw new Error('Não foi possível baixar o vídeo')
  }
  const filename = filenameFromDisposition(
    response.headers.get('Content-Disposition'),
    `${originalFilename}.zip`,
  )
  if (handle) {
    try {
      const writable = await handle.createWritable()
      await response.body.pipeTo(writable)
    } catch (error) {
      if (!response.body.locked) await response.body.cancel().catch(() => undefined)
      throw error
    }
    return
  }

  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
