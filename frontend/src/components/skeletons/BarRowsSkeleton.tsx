import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from './LoadingStatus'

interface BarRowsSkeletonProps {
  label: string
  rows?: number
}

// "Name + value, then a bar" rows, as in the product and table performance lists.
export const BarRowsSkeleton = ({ label, rows = 5 }: BarRowsSkeletonProps) => (
  <div className="flex flex-col gap-5">
    <LoadingStatus label={label} />
    {Array.from({ length: rows }, (_, i) => (
      <div key={i} data-testid="skeleton-bar-row" className="flex flex-col gap-2">
        <div className="flex items-center justify-between gap-2">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-4 w-24" />
        </div>
        <Skeleton className="h-1.5 w-full rounded-full" />
      </div>
    ))}
  </div>
)
