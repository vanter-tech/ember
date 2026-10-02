import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'

// The read-only plan card before the subscription arrives: header, the big plan name with its
// status chip, the period end, a few detail rows and the feature list. No footer: nothing to save.
export const PlanSkeleton = ({ label }: { label: string }) => (
  <Card className="border-zinc-100 shadow-sm">
    <LoadingStatus label={label} />
    <CardHeader data-testid="skeleton-settings-header" className="flex flex-row items-center gap-4 space-y-0 p-6">
      <Skeleton className="size-12 rounded-full" />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-4 w-72 max-w-full" />
      </div>
    </CardHeader>
    <div className="m-auto w-full border-t border-[#7a1315]/20" />
    <CardContent className="flex flex-col gap-6 p-6">
      <div className="flex items-center gap-3">
        <Skeleton data-testid="skeleton-plan-name" className="h-10 w-40" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-36" />
        <Skeleton className="h-8 w-52" />
      </div>
      <div className="flex flex-col gap-3">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} data-testid="skeleton-plan-row" className="flex items-center justify-between gap-4">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-4 w-40" />
          </div>
        ))}
      </div>
      <div className="flex flex-col gap-2">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-4 w-64 max-w-full" />
        ))}
      </div>
    </CardContent>
  </Card>
)
