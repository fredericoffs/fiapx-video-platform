import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { PageSize } from '@/features/admin/api/queries'
import { Button } from '@/shared/ui/button'

const PAGE_SIZES: PageSize[] = [10, 20]

interface AdminPaginationProps {
  page: number
  size: PageSize
  totalElements: number
  onPageChange: (page: number) => void
  onSizeChange: (size: PageSize) => void
}

export function AdminPagination({
  page,
  size,
  totalElements,
  onPageChange,
  onSizeChange,
}: AdminPaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalElements / size))
  const currentPage = page + 1

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
      <div className="flex items-center gap-2 text-muted-foreground">
        <span>Itens por página:</span>
        <div className="flex gap-1">
          {PAGE_SIZES.map((pageSize) => (
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
