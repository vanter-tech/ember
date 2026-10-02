import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'
import { PageHeaderSkeleton } from '@/components/skeletons/PageHeaderSkeleton'
import { TableSkeleton } from '@/components/skeletons/TableSkeleton'

const CARD = 'border border-border/40 bg-background py-6 shadow-sm'

// Loading state of the accountant cash register, with the frame of the real page: header, the
// current-shift card (meta line, four stat tiles, action buttons), and the movements and payments
// tables. No real text: everything is a placeholder block.
export const CashRegisterSkeleton = ({ label }: { label: string }) => (
  <div className="flex flex-col gap-8">
    <LoadingStatus label={label} />
    <PageHeaderSkeleton />

    <div className="flex flex-col gap-6">
      <Card data-testid="skeleton-shift-card" className={CARD}>
        <CardHeader className="flex flex-row items-center justify-between">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-6 w-20 rounded-full" />
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
            <Skeleton className="h-4 w-44" />
            <Skeleton className="h-4 w-36" />
            <Skeleton className="h-4 w-48" />
          </div>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {Array.from({ length: 4 }, (_, i) => (
              <div
                key={i}
                data-testid="skeleton-stat-tile"
                className={`flex flex-col gap-3 rounded-2xl p-4 ${i === 3 ? 'col-span-2 bg-primary/10 lg:col-span-1' : 'bg-zinc-50'}`}
              >
                <Skeleton className="h-3 w-24" />
                <Skeleton className="h-6 w-28" />
              </div>
            ))}
          </div>
          <div className="flex flex-wrap items-start gap-2">
            <Skeleton className="h-10 w-36" />
            <Skeleton className="h-10 w-40" />
            <Skeleton className="h-10 w-32" />
          </div>
        </CardContent>
      </Card>

      <Card className={CARD}>
        <CardHeader>
          <Skeleton className="h-4 w-28" />
        </CardHeader>
        <CardContent>
          <TableSkeleton columns={5} rows={3} />
        </CardContent>
      </Card>

      <Card className={CARD}>
        <CardHeader>
          <Skeleton className="h-4 w-24" />
        </CardHeader>
        <CardContent>
          <TableSkeleton columns={8} rows={4} />
        </CardContent>
      </Card>
    </div>
  </div>
)
