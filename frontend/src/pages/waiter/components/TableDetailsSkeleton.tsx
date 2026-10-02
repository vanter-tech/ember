import { Skeleton } from '@/components/ui/skeleton'

// The right-hand panel of the floor with a table selected (number + waiter, status, order lines,
// action buttons), drawn as placeholders while the floor loads so the page has its final frame.
export const TableDetailsSkeleton = () => (
  <div data-testid="skeleton-panel" className="rounded-2xl bg-white p-6">
    <div className="flex items-center justify-between">
      <Skeleton className="h-9 w-20" />
      <div className="flex flex-col items-end gap-2">
        <Skeleton className="h-3 w-14" />
        <Skeleton className="h-4 w-24" />
      </div>
    </div>
    <div className="mt-4 flex items-center gap-2">
      <Skeleton className="size-5 rounded-full" />
      <Skeleton className="h-4 w-20" />
    </div>
    <div className="my-6 flex flex-col gap-4 border-y border-zinc-100 py-4">
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} data-testid="skeleton-panel-item" className="flex items-center justify-between">
          <div className="flex flex-col gap-2">
            <Skeleton className="h-4 w-28" />
            <Skeleton className="h-3 w-16" />
          </div>
          <Skeleton className="h-4 w-12" />
        </div>
      ))}
    </div>
    <div className="mt-6 flex flex-col gap-4">
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-10 w-full" />
    </div>
  </div>
)
