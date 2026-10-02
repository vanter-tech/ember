import { Skeleton } from '@/components/ui/skeleton'

// The floor header (title + occupied/free legend) as placeholder blocks while the floor loads.
export const FloorHeaderSkeleton = () => (
  <div data-testid="skeleton-floor-header" className="mb-5 flex items-center justify-between">
    <Skeleton className="h-7 w-44" />
    <div className="flex gap-4">
      <div className="flex items-center gap-2">
        <Skeleton className="size-5 rounded-full" />
        <Skeleton className="h-4 w-16" />
      </div>
      <div className="flex items-center gap-2">
        <Skeleton className="size-5 rounded-full" />
        <Skeleton className="h-4 w-12" />
      </div>
    </div>
  </div>
)
