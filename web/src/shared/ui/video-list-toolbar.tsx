import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import {
  DEFAULT_VIDEO_LIST_FILTERS,
  hasActiveDateFilter,
  type SortDirection,
  type VideoListFilters,
  type VideoSortField,
} from '@/shared/api/videos'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

const SORT_OPTIONS: { value: `${VideoSortField}:${SortDirection}`; label: string }[] = [
  { value: 'CREATED_AT:DESC', label: 'Mais recentes' },
  { value: 'CREATED_AT:ASC', label: 'Mais antigos' },
  { value: 'FILENAME:ASC', label: 'Nome (A–Z)' },
  { value: 'FILENAME:DESC', label: 'Nome (Z–A)' },
  { value: 'FILE_SIZE:DESC', label: 'Maior tamanho' },
  { value: 'FILE_SIZE:ASC', label: 'Menor tamanho' },
]

interface VideoListToolbarProps {
  filters: VideoListFilters
  onChange: (filters: VideoListFilters) => void
  /** Filtros extras da tela (ex.: busca por nome no admin), antes dos de data. */
  children?: ReactNode
}

export function VideoListToolbar({ filters, onChange, children }: VideoListToolbarProps) {
  const isDefault =
    !hasActiveDateFilter(filters) &&
    filters.sortBy === DEFAULT_VIDEO_LIST_FILTERS.sortBy &&
    filters.direction === DEFAULT_VIDEO_LIST_FILTERS.direction

  return (
    <div className="flex flex-wrap items-end gap-3">
      {children}
      <div className="flex flex-col gap-1">
        <Label htmlFor="video-filter-from" className="text-xs text-muted-foreground">
          Enviado de
        </Label>
        <Input
          id="video-filter-from"
          type="datetime-local"
          className="w-auto"
          value={filters.createdFrom}
          max={filters.createdTo || undefined}
          onChange={(event) => {
            onChange({ ...filters, createdFrom: event.target.value })
          }}
        />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="video-filter-to" className="text-xs text-muted-foreground">
          Até
        </Label>
        <Input
          id="video-filter-to"
          type="datetime-local"
          className="w-auto"
          value={filters.createdTo}
          min={filters.createdFrom || undefined}
          onChange={(event) => {
            onChange({ ...filters, createdTo: event.target.value })
          }}
        />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="video-sort" className="text-xs text-muted-foreground">
          Ordenar por
        </Label>
        <select
          id="video-sort"
          className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
          value={`${filters.sortBy}:${filters.direction}`}
          onChange={(event) => {
            const [sortBy, direction] = event.target.value.split(':') as [
              VideoSortField,
              SortDirection,
            ]
            onChange({ ...filters, sortBy, direction })
          }}
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      {!isDefault && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            onChange(DEFAULT_VIDEO_LIST_FILTERS)
          }}
        >
          <X />
          Limpar
        </Button>
      )}
    </div>
  )
}
