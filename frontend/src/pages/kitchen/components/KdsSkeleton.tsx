import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'

// One small order card of the queue row: table / ticket / elapsed time on the left, the dishes
// with their status badge and action button on the right (same 300px height as QueueCard).
const QueueCardSkeleton = () => (
  <Card
    data-testid="skeleton-queue-card"
    className="flex h-[300px] w-[420px] shrink-0 flex-row gap-0 border-l-8 border-zinc-200 p-0"
  >
    <div className="flex flex-col gap-3 p-6">
      <Skeleton className="h-8 w-14" />
      <Skeleton className="h-3 w-24" />
      <Skeleton className="mt-2 h-4 w-20" />
    </div>
    <div className="flex flex-1 flex-col gap-5 px-6 py-6">
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} className="flex items-center justify-between gap-3">
          <div className="flex flex-col gap-2">
            <Skeleton className="h-4 w-28" />
            <Skeleton className="h-5 w-20 rounded-full" />
          </div>
          <Skeleton className="h-8 w-20" />
        </div>
      ))}
    </div>
  </Card>
)

// One of the three status columns of the focused card (pending / preparing / ready).
const ColumnSkeleton = () => (
  <section data-testid="skeleton-kds-column" className="flex flex-col gap-3 rounded-2xl bg-gray-50 p-3">
    <header className="flex items-center justify-between px-1">
      <Skeleton className="h-6 w-28" />
      <Skeleton className="h-5 w-7 rounded-full" />
    </header>
    {Array.from({ length: 2 }, (_, i) => (
      <div key={i} className="flex items-center gap-3 rounded-2xl border border-gray-200 bg-white px-4 py-3">
        <Skeleton className="size-5 shrink-0 rounded-full" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-3 w-1/2" />
        </div>
        <Skeleton className="h-8 w-20" />
      </div>
    ))}
  </section>
)

// The big first order: heading, ticket / entry time, the action buttons and the three columns.
const FocusedCardSkeleton = () => (
  <Card data-testid="skeleton-focused-card" className="rounded-3xl border-l-8 border-zinc-200 p-5">
    <CardHeader className="flex flex-col gap-3 border-b">
      <Skeleton className="h-8 w-72" />
      <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex gap-3">
          <Skeleton className="h-3 w-28" />
          <Skeleton className="h-3 w-36" />
        </div>
        <div className="flex items-center gap-3">
          <Skeleton className="h-8 w-32 rounded-lg" />
          <Skeleton className="h-12 w-28" />
        </div>
      </div>
    </CardHeader>
    <CardContent>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <ColumnSkeleton />
        <ColumnSkeleton />
        <ColumnSkeleton />
      </div>
    </CardContent>
  </Card>
)

// The screen header (brand title, subtitle and connection badge) as placeholder blocks.
export const KdsHeaderSkeleton = () => (
  <div
    data-testid="skeleton-kds-header"
    className="relative flex h-20 w-full flex-col items-center justify-center gap-2 rounded-3xl p-4 shadow-sm"
  >
    <Skeleton className="absolute right-4 top-3 h-6 w-28 rounded-full" />
    <Skeleton className="h-8 w-28" />
    <Skeleton className="h-3 w-44" />
  </div>
)

// Loading state of the KDS board below its header: the queue row followed by the focused card.
export const KdsSkeleton = ({ label }: { label: string }) => (
  <>
    <LoadingStatus label={label} />
    <div className="flex items-start gap-6 overflow-x-auto p-6">
      {Array.from({ length: 3 }, (_, i) => (
        <QueueCardSkeleton key={i} />
      ))}
    </div>
    <div className="w-full px-6 pb-6">
      <FocusedCardSkeleton />
    </div>
  </>
)
