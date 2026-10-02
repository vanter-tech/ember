import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { LoadingStatus } from './LoadingStatus'

interface CardGridSkeletonProps {
  label: string
  count?: number
  /** Grid classes of the real grid (columns, gap) so the placeholder has the same shape. */
  className?: string
  /** Size of one placeholder card, e.g. the height of the real card. */
  itemClassName?: string
}

export const CardGridSkeleton = ({
  label,
  count = 6,
  className = 'grid-cols-2 sm:grid-cols-3',
  itemClassName = 'h-40',
}: CardGridSkeletonProps) => (
  <>
    <LoadingStatus label={label} />
    <div className={cn('grid gap-4', className)}>
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} data-testid="skeleton-card" className={cn('rounded-2xl', itemClassName)} />
      ))}
    </div>
  </>
)
