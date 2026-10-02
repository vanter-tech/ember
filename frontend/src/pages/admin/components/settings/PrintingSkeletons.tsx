import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'

const DIVIDER = 'm-auto w-full border-t border-[#7a1315]/20'

// The printer line under an agent: "role · label · connection" and the remove button.
export const PrinterRowSkeleton = () => (
  <div data-testid="skeleton-printer-row" className="flex items-center justify-between gap-2 pl-3">
    <Skeleton className="h-4 w-64 max-w-full" />
    <Skeleton className="size-7" />
  </div>
)

// An agent: name and status line on the left, its four action buttons on the right, then its printers.
const AgentRowSkeleton = () => (
  <div data-testid="skeleton-agent-row" className="space-y-2 rounded-xl border border-zinc-200 p-3">
    <div className="flex items-center justify-between gap-3">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-4 w-56" />
        <Skeleton className="h-3 w-40" />
      </div>
      <div className="flex items-center gap-2">
        <Skeleton className="h-8 w-32 rounded-xl" />
        <Skeleton className="h-8 w-32 rounded-xl" />
        <Skeleton className="h-8 w-36 rounded-xl" />
        <Skeleton className="size-8" />
      </div>
    </div>
    <PrinterRowSkeleton />
  </div>
)

// The recent print jobs card: a title block and rows of "role · status" with an action button.
// `label` is given when this card is the only thing loading, so it announces itself.
export const JobsCardSkeleton = ({ label }: { label?: string }) => (
  <Card className="rounded-2xl border-zinc-200">
    {label && <LoadingStatus label={label} />}
    <CardHeader className="flex flex-row items-center justify-between">
      <Skeleton className="h-6 w-44" />
    </CardHeader>
    <div className={DIVIDER} />
    <CardContent className="space-y-2 py-4">
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i} data-testid="skeleton-job-row" className="flex items-center justify-between rounded-xl border border-zinc-200 p-3">
          <div className="flex flex-col gap-1.5">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-3 w-40" />
          </div>
          <Skeleton className="h-8 w-28 rounded-xl" />
        </div>
      ))}
    </CardContent>
  </Card>
)

// The whole Printers tab before the agents arrive: header, agents card (title, download link,
// "generate agent" button, two agents with their printers) and the jobs card.
export const PrintingSkeleton = ({ label }: { label: string }) => (
  <Card className="border-zinc-100 shadow-sm">
    <LoadingStatus label={label} />
    <CardHeader data-testid="skeleton-settings-header" className="flex flex-row items-center gap-4 space-y-0 p-6">
      <Skeleton className="size-12 shrink-0 rounded-full" />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
    </CardHeader>
    <div className={DIVIDER} />
    <CardContent className="space-y-6 p-6">
      <Card className="rounded-2xl border-zinc-200">
        <CardHeader className="flex flex-row items-center justify-between">
          <Skeleton className="h-6 w-48" />
          <div className="flex items-center gap-3">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-9 w-36 rounded-xl" />
          </div>
        </CardHeader>
        <div className={DIVIDER} />
        <CardContent className="space-y-2 py-4">
          <AgentRowSkeleton />
          <AgentRowSkeleton />
        </CardContent>
      </Card>
      <JobsCardSkeleton />
    </CardContent>
  </Card>
)
