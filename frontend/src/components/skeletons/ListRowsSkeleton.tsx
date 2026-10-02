import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from './LoadingStatus'

interface ListRowsSkeletonProps {
  label: string
  rows?: number
}

// Rows of "avatar + two lines + action", for lists and table-like views.
export const ListRowsSkeleton = ({ label, rows = 5 }: ListRowsSkeletonProps) => (
  <div className="flex flex-col gap-3">
    <LoadingStatus label={label} />
    {Array.from({ length: rows }, (_, i) => (
      <div key={i} data-testid="skeleton-row" className="flex items-center gap-4 rounded-2xl bg-white p-4">
        <Skeleton className="size-10 shrink-0 rounded-full" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-3 w-1/2" />
        </div>
        <Skeleton className="h-8 w-20" />
      </div>
    ))}
  </div>
)
