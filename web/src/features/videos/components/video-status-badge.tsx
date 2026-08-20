import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, Clock, Loader2, XCircle } from 'lucide-react'
import { Badge } from '@/shared/ui/badge'
import { cn } from '@/shared/lib/utils'
import type { VideoStatus } from '@/shared/api/videos'

const STATUS_CONFIG: Record<VideoStatus, { label: string; icon: typeof Clock; className: string }> =
  {
    QUEUED: { label: 'Na fila', icon: Clock, className: '' },
    PROCESSING: {
      label: 'Processando',
      icon: Loader2,
      className: 'bg-primary text-primary-foreground',
    },
    COMPLETED: {
      label: 'Concluído',
      icon: CheckCircle2,
      className: 'border-emerald-600/30 bg-emerald-600/10 text-emerald-600 dark:text-emerald-400',
    },
    FAILED: {
      label: 'Falhou',
      icon: XCircle,
      className: 'border-destructive/30 bg-destructive/10 text-destructive',
    },
  }

export function VideoStatusBadge({ status }: { status: VideoStatus }) {
  const { label, icon: Icon, className } = STATUS_CONFIG[status]

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.span
        key={status}
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.9 }}
        transition={{ duration: 0.15 }}
        className="inline-flex"
      >
        <Badge variant={status === 'QUEUED' ? 'outline' : 'default'} className={cn(className)}>
          <Icon className={cn('size-3', status === 'PROCESSING' && 'animate-spin')} />
          {label}
        </Badge>
      </motion.span>
    </AnimatePresence>
  )
}
