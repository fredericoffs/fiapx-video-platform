import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/shared/ui/button'

interface PaginationProps<S extends number> {
  page: number
  size: S
  totalElements: number
  onPageChange: (page: number) => void
  /** Sem estes dois, o tamanho de página é fixo e o seletor não aparece. */
  pageSizes?: readonly S[]
  onSizeChange?: (size: S) => void
}

export function Pagination<S extends number>({
  page,
  size,
  totalElements,
  onPageChange,
  pageSizes,
  onSizeChange,
}: PaginationProps<S>) {
  const totalPages = Math.max(1, Math.ceil(totalElements / size))
  const currentPage = page + 1

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
      {pageSizes && onSizeChange ? (
        <div className="flex items-center gap-2 text-muted-foreground">
          <span>Itens por página:</span>
          <div className="flex gap-1">
            {pageSizes.map((pageSize) => (
              <Button
                key={pageSize}
                size="sm"
                variant={size === pageSize ? 'secondary' : 'ghost'}
                onClick={() => {
                  onSizeChange(pageSize)
                }}
                aria-pressed={size === pageSize}
              >
                {pageSize}
              </Button>
            ))}
          </div>
        </div>
      ) : (
        <span className="text-muted-foreground">
          {totalElements} {totalElements === 1 ? 'vídeo' : 'vídeos'}
        </span>
      )}

      <div className="flex items-center gap-2">
        <Button
          size="icon-sm"
          variant="outline"
          disabled={page <= 0}
          onClick={() => {
            onPageChange(page - 1)
          }}
          aria-label="Página anterior"
        >
          <ChevronLeft />
        </Button>
        <span className="text-muted-foreground">
          Página {currentPage} de {totalPages}
        </span>
        <Button
          size="icon-sm"
          variant="outline"
          disabled={currentPage >= totalPages}
          onClick={() => {
            onPageChange(page + 1)
          }}
          aria-label="Próxima página"
        >
          <ChevronRight />
        </Button>
      </div>
    </div>
  )
}
