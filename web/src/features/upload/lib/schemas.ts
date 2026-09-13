import { z } from 'zod'

/** Espelho aqui VideoFormatValidator.ALLOWED_EXTENSIONS (video-api). */
export const ALLOWED_VIDEO_EXTENSIONS = ['mp4', 'mov', 'avi', 'mkv', 'webm'] as const

/** Espelho aqui spring.servlet.multipart.max-file-size (video-api/application.yml). */
export const MAX_VIDEO_FILE_SIZE_BYTES = 500 * 1024 * 1024
export const MAX_VIDEO_FILE_SIZE_MB = String(Math.round(MAX_VIDEO_FILE_SIZE_BYTES / (1024 * 1024)))

function extractExtension(filename: string): string {
  const dotIndex = filename.lastIndexOf('.')
  if (dotIndex < 0 || dotIndex === filename.length - 1) {
    return ''
  }
  return filename.slice(dotIndex + 1).toLowerCase()
}

export function isAllowedVideoFile(file: File): boolean {
  const extension = extractExtension(file.name)
  return (ALLOWED_VIDEO_EXTENSIONS as readonly string[]).includes(extension)
}

export const videoFileSchema = z
  .instanceof(File)
  .refine(isAllowedVideoFile, {
    message: `Formato não suportado — use ${ALLOWED_VIDEO_EXTENSIONS.join(', ')}`,
  })
  .refine((file) => file.size <= MAX_VIDEO_FILE_SIZE_BYTES, {
    message: `Arquivo maior que ${MAX_VIDEO_FILE_SIZE_MB}MB — reduza o tamanho e tente de novo`,
  })
