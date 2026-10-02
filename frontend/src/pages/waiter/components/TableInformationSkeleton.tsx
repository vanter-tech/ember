import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'

// Loading state of the waiter's table detail page, with the same frame as the real one: header
// (back, number, status, waiter, round action buttons) and the 3-column grid with the orders,
// participants and activity cards on the left and the bill summary on the right. Everything is a
// placeholder block, card titles included: a loading state shows no real text.
const CardShell = ({ children }: { children: React.ReactNode }) => (
  <Card className="relative overflow-hidden rounded-3xl border-none shadow-sm">
    <div className="absolute top-0 left-0 h-1 w-full bg-linear-to-r from-transparent via-[#8B0000] to-transparent opacity-20" />
    <CardHeader className="border-b border p-7">
      <Skeleton data-testid="skeleton-card-title" className="h-8 w-56" />
    </CardHeader>
    {children}
  </Card>
)

export const TableInformationSkeleton = ({ label }: { label: string }) => {
  return (
    <>
      <LoadingStatus label={label} />

      <div
        data-testid="skeleton-detail-header"
        className="mb-6 flex flex-col gap-4 md:flex-row md:items-start md:justify-between"
      >
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-3 p-2 sm:gap-4 sm:p-5 sm:pb-0">
            <Skeleton className="size-11 rounded-full sm:size-13" />
            <Skeleton className="h-10 w-40" />
            <Skeleton className="h-10 w-32 rounded-full" />
          </div>
          <div className="flex items-center gap-2 pl-2 sm:pl-9">
            <Skeleton className="size-6 rounded-full" />
            <Skeleton className="h-4 w-48" />
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2 sm:gap-3 sm:pr-7">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="size-12 rounded-full sm:size-18" />
          ))}
          <Skeleton className="h-12 w-36 rounded-xl" />
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          <CardShell>
            <CardContent className="flex flex-col gap-3">
              {Array.from({ length: 3 }, (_, i) => (
                <div
                  key={i}
                  data-testid="skeleton-order-row"
                  className="flex items-center justify-between rounded-2xl bg-gray-50/80 p-4"
                >
                  <div className="flex items-center gap-4">
                    <Skeleton className="size-5 rounded-md" />
                    <Skeleton className="size-10 rounded-full" />
                    <div className="flex flex-col gap-2">
                      <Skeleton className="h-4 w-32" />
                      <Skeleton className="h-3 w-20" />
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <Skeleton className="h-4 w-14" />
                    <Skeleton className="h-10 w-12" />
                  </div>
                </div>
              ))}
            </CardContent>
          </CardShell>

          <CardShell>
            <CardContent className="grid grid-cols-2 gap-4">
              {Array.from({ length: 2 }, (_, i) => (
                <div
                  key={i}
                  data-testid="skeleton-participant"
                  className="flex items-center gap-3 rounded-3xl bg-gray-100 p-3"
                >
                  <Skeleton className="size-10 rounded-full" />
                  <Skeleton className="h-4 w-24" />
                </div>
              ))}
            </CardContent>
          </CardShell>

          <CardShell>
            <CardContent>
              <div className="ml-3 flex flex-col gap-6 border-l-2 border-gray-200 pl-5 pt-2">
                {Array.from({ length: 3 }, (_, i) => (
                  <div key={i} data-testid="skeleton-activity-entry" className="flex flex-col gap-2">
                    <Skeleton className="h-3 w-56" />
                    <Skeleton className="h-3 w-24" />
                  </div>
                ))}
              </div>
            </CardContent>
          </CardShell>
        </div>

        <div className="lg:col-span-1">
          <Card data-testid="skeleton-bill">
            <CardHeader className="border-b border p-7">
              <Skeleton data-testid="skeleton-card-title" className="h-8 w-40" />
            </CardHeader>
            <CardContent className="flex flex-col gap-5 pt-4">
              <div className="flex items-center justify-between px-4">
                <Skeleton className="h-5 w-24" />
                <Skeleton className="h-5 w-16" />
              </div>
              <div className="flex items-center justify-between px-4">
                <Skeleton className="h-5 w-28" />
                <Skeleton className="h-5 w-14" />
              </div>
            </CardContent>
            <CardFooter className="flex flex-col gap-3">
              <div className="flex w-full items-center justify-between p-4">
                <Skeleton className="h-7 w-20" />
                <Skeleton className="h-8 w-24" />
              </div>
              <Skeleton className="h-15 w-full rounded-xl" />
            </CardFooter>
          </Card>
        </div>
      </div>
    </>
  )
}
