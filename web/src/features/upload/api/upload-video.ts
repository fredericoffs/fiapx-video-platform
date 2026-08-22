import { baseUrl } from '@/shared/api/client'
import { useSessionStore } from '@/shared/lib/session-store'
import type { components } from '@/shared/api/schema.gen'

export class UploadError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'UploadError'
    this.status = status
  }
}

function messageForStatus(status: number): string {
  if (status === 400) {
    return 'Formato de vídeo não suportado'
  }
  if (status === 401) {
    return 'Sessão expirada — faça login de novo'
  }
  if (status === 429) {
    return 'Limite de uploads atingido — aguarde um pouco antes de tentar de novo'
  }
  return 'Não foi possível enviar o vídeo. Tente novamente.'
}

/**
 * Uso XMLHttpRequest bruto pro upload (não openapi-fetch/fetch): é o único jeito
 * de observar `upload.onprogress` para uma barra de progresso real.
 */
export function uploadVideo(
  file: File,
  onProgress: (percent: number) => void,
): Promise<components['schemas']['VideoUploadResponse']> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${baseUrl}/videos`)

    const token = useSessionStore.getState().session?.token
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status === 201) {
        try {
          resolve(JSON.parse(xhr.responseText) as components['schemas']['VideoUploadResponse'])
        } catch {
          reject(new UploadError(messageForStatus(xhr.status), xhr.status))
        }
        return
      }
      reject(new UploadError(messageForStatus(xhr.status), xhr.status))
    })

    xhr.addEventListener('error', () => {
      reject(new UploadError('Falha de rede durante o envio', 0))
    })

    const formData = new FormData()
    formData.append('file', file)
    xhr.send(formData)
  })
}
