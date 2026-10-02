import { Skeleton } from '@/components/ui/skeleton'

// A page's title and subtitle as placeholder blocks, with an optional round icon block in front of
// the title (the cash receipts view has one).
export const PageHeaderSkeleton = ({ withIcon = false }: { withIcon?: boolean }) => (
  <div data-testid="skeleton-page-header" className="flex flex-col gap-2">
    <div className="flex items-center gap-3">
      {withIcon && <Skeleton data-testid="skeleton-page-header-icon" className="size-11 shrink-0 rounded-full" />}
      <Skeleton className="h-9 w-64" />
    </div>
    <Skeleton className="h-4 w-80 max-w-full" />
  </div>
)
