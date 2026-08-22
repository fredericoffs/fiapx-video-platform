import { baseUrl } from '@/shared/api/client'
import { useSessionStore } from '@/shared/lib/session-store'

function filenameFromDisposition(header: string | null, fallback: string): string {
  const match = header ? /filename="?([^"]+)"?/.exec(header) : null
  return match?.[1] ?? fallback
}

/**
 * Faço um download autenticado: o endpoint exige Authorization: Bearer, então não
 * dá pra usar um <a href> puro — preciso buscar como blob e disparar o
 * download via link temporário.
 */
export async function downloadVideo(videoId: string, originalFilename: string): Promise<void> {
  const token = useSessionStore.getState().session?.token
  const headers = new Headers()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${baseUrl}/videos/${videoId}/download`, { headers })

  if (!response.ok) {
    throw new Error('Não foi possível baixar o vídeo')
  }

  const blob = await response.blob()
  const filename = filenameFromDisposition(
    response.headers.get('Content-Disposition'),
    `${originalFilename}.zip`,
  )

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
