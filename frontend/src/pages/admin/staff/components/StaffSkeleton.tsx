import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'
import { PageHeaderSkeleton } from '@/components/skeletons/PageHeaderSkeleton'

const CARD = 'border border-border/40 bg-background py-6 shadow-sm'

// A staff card as it is: avatar and role chip, name and email, detail chips, profile + deactivate buttons.
const StaffCardSkeleton = () => (
  <Card data-testid="skeleton-staff-card" className={CARD}>
    <CardContent className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-2">
        <Skeleton data-testid="skeleton-staff-avatar" className="size-12 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
      <div className="flex flex-col gap-2">
        <Skeleton className="h-5 w-36" />
        <Skeleton className="h-4 w-44" />
      </div>
      <div className="flex flex-wrap gap-2">
        <Skeleton className="h-6 w-24 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
      <div data-testid="skeleton-staff-actions" className="flex items-center gap-2 pt-1">
        <Skeleton className="h-8 flex-1" />
        <Skeleton className="size-8" />
      </div>
    </CardContent>
  </Card>
)

// Loading state of the staff page with its frame: header, filter pills, member cards, KPI cards.
// No real text: every title, label and button is a placeholder block.
export const StaffSkeleton = ({ label }: { label: string }) => (
  <div className="flex flex-col gap-8">
    <LoadingStatus label={label} />
    <PageHeaderSkeleton />
    <div className="flex w-fit items-center gap-1 rounded-full bg-muted/60 p-1">
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} data-testid="skeleton-pill" className="h-8 w-20 rounded-full" />
      ))}
    </div>
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {Array.from({ length: 8 }, (_, i) => (
        <StaffCardSkeleton key={i} />
      ))}
    </div>
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
      {Array.from({ length: 3 }, (_, i) => (
        <Card key={i} data-testid="skeleton-kpi" className={CARD}>
          <CardHeader className="flex flex-row items-center justify-between">
            <Skeleton className="h-3 w-28" />
            <Skeleton className="size-9 rounded-full" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-9 w-20" />
          </CardContent>
        </Card>
      ))}
    </div>
  </div>
)
