import { z } from 'zod'

/** Espelho aqui VideoFormatValidator.ALLOWED_EXTENSIONS (video-api). */
export const ALLOWED_VIDEO_EXTENSIONS = ['mp4', 'mov', 'avi', 'mkv', 'webm'] as const

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

export const videoFileSchema = z.instanceof(File).refine(isAllowedVideoFile, {
  message: `Formato não suportado — use ${ALLOWED_VIDEO_EXTENSIONS.join(', ')}`,
})
