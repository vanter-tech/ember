import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'
import { TableSkeleton } from '@/components/skeletons/TableSkeleton'

// The rewards catalog card: title, description and the "new reward" button, then a 4-column table.
export const LoyaltyRewardsSkeleton = ({ label }: { label: string }) => (
  <Card className="border-zinc-100 shadow-sm">
    <LoadingStatus label={label} />
    <CardHeader data-testid="skeleton-settings-header" className="flex flex-row items-center justify-between gap-4 space-y-0 p-6">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-4 w-72 max-w-full" />
      </div>
      <Skeleton className="h-9 w-36" />
    </CardHeader>
    <div className="m-auto w-full border-t border-[#7a1315]/20" />
    <CardContent>
      <TableSkeleton columns={4} rows={4} />
    </CardContent>
  </Card>
)
