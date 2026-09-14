const UNITS = ['B', 'KB', 'MB', 'GB', 'TB'] as const

/** Formata bytes como "12,4 MB" (pt-BR) — null/negativo vira null pra o chamador decidir o que exibir. */
export function formatFileSize(bytes: number | null | undefined): string | null {
  if (bytes === null || bytes === undefined || bytes < 0 || Number.isNaN(bytes)) {
    return null
  }
  if (bytes === 0) {
    return '0 B'
  }

  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), UNITS.length - 1)
  const value = bytes / 1024 ** exponent
  const decimals = exponent === 0 ? 0 : 1
  const unit = UNITS[exponent] ?? 'B'

  return `${value.toLocaleString('pt-BR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })} ${unit}`
}
