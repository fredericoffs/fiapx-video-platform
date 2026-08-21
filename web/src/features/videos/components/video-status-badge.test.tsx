import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { VideoStatusBadge } from './video-status-badge'
import type { VideoStatus } from '@/shared/api/videos'

const EXPECTED_LABEL: Record<VideoStatus, string> = {
  QUEUED: 'Na fila',
  PROCESSING: 'Processando',
  COMPLETED: 'Concluído',
  FAILED: 'Falhou',
}

describe('VideoStatusBadge', () => {
  it.each(Object.entries(EXPECTED_LABEL) as [VideoStatus, string][])(
    'renderiza o rótulo correto para %s',
    (status, label) => {
      render(<VideoStatusBadge status={status} />)
      expect(screen.getByText(label)).toBeInTheDocument()
    },
  )
})
