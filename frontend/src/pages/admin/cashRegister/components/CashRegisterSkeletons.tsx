import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'
import { PageHeaderSkeleton } from '@/components/skeletons/PageHeaderSkeleton'
import { TableSkeleton } from '@/components/skeletons/TableSkeleton'

const CARD = 'border border-border/40 bg-background py-6 shadow-sm'

// The shift history card: its 7-column table as placeholders.
export const ShiftHistorySkeleton = ({ label }: { label: string }) => (
  <>
    <LoadingStatus label={label} />
    <Card className={CARD}>
      <CardContent>
        <TableSkeleton columns={7} rows={8} />
      </CardContent>
    </Card>
  </>
)

// The daily (Z) report below its date picker: five figure cards and the shifts table.
export const DailyReportSkeleton = ({ label }: { label: string }) => (
  <>
    <LoadingStatus label={label} />
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 5 }, (_, i) => (
        <Card key={i} data-testid="skeleton-card" className="border border-border/40 bg-background py-8 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-start gap-3">
            <Skeleton className="size-10 rounded-full" />
            <Skeleton className="h-3 w-32" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-9 w-32" />
          </CardContent>
        </Card>
      ))}
    </div>
    <Card className={CARD}>
      <CardHeader>
        <Skeleton className="h-4 w-28" />
      </CardHeader>
      <CardContent>
        <TableSkeleton columns={7} rows={3} />
      </CardContent>
    </Card>
  </>
)

// The whole admin cash register page on first load: header with the manual-open button, the
// section sidebar (one block on mobile, the three nav buttons on desktop) and the history table.
export const CashRegisterPageSkeleton = ({ label }: { label: string }) => (
  <div className="flex flex-col gap-8">
    <div className="flex items-start justify-between gap-4">
      <PageHeaderSkeleton />
      <Skeleton data-testid="skeleton-manual-open" className="h-9 w-44 shrink-0" />
    </div>
    <div className="flex flex-col gap-4 md:flex-row md:gap-8">
      <div data-testid="skeleton-cash-sidebar" className="w-full shrink-0 md:w-64">
        <Skeleton className="h-9 w-full md:hidden" />
        <div className="hidden flex-col gap-2 md:flex">
          <Skeleton className="h-9 w-full" />
          <div className="flex flex-col gap-1 pl-6">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        </div>
      </div>
      <div className="min-w-0 flex-1">
        <ShiftHistorySkeleton label={label} />
      </div>
    </div>
  </div>
)
