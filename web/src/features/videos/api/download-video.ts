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
  const response = await fetch(`${baseUrl}/videos/${videoId}/download`, { headers })

  if (!response.ok || !response.body) {
    throw new Error('Não foi possível baixar o vídeo')
  }

  const filename = filenameFromDisposition(
    response.headers.get('Content-Disposition'),
    `${originalFilename}.zip`,
  )

  if (typeof window.showSaveFilePicker === 'function') {
    let handle: SaveFileHandle
    try {
      handle = await window.showSaveFilePicker({ suggestedName: filename })
    } catch (error) {
      // Usuário cancelou o diálogo "salvar como" — respeito a decisão, não caio pro
      // download via Blob por baixo dos panos (seria uma segunda surpresa).
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      throw error
    }
    const writable = await handle.createWritable()
    await response.body.pipeTo(writable)
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
