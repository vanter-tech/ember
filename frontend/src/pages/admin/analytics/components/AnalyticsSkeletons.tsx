import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { BarRowsSkeleton } from '@/components/skeletons/BarRowsSkeleton'
import { ChartSkeleton } from '@/components/skeletons/ChartSkeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'

const CARD = 'border border-border/40 bg-background py-6 shadow-sm'

// Icon square + title of an analytics card, as placeholder blocks; `withSubtitle` adds the summary line under the title.
const CardTitleSkeleton = ({
  titleClassName = 'w-44',
  withSubtitle = false,
}: {
  titleClassName?: string
  withSubtitle?: boolean
}) => (
  <div data-testid="skeleton-card-title" className="flex items-center gap-2.5">
    <Skeleton className="size-8 rounded-lg" />
    <div className="flex flex-col gap-1.5">
      <Skeleton className={`h-5 ${titleClassName}`} />
      {withSubtitle && <Skeleton data-testid="skeleton-subtitle" className="h-3 w-36" />}
    </div>
  </div>
)

// The four headline cards (icon + label, then the big figure).
export const SummaryCardsSkeleton = ({ label }: { label: string }) => (
  <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
    <LoadingStatus label={label} />
    {Array.from({ length: 4 }, (_, i) => (
      <Card key={i} data-testid="skeleton-card" className={CARD}>
        <CardHeader className="flex flex-row items-center justify-start gap-3">
          <Skeleton className="size-9 rounded-full" />
          <Skeleton className="h-3 w-32" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-9 w-36" />
        </CardContent>
      </Card>
    ))}
  </div>
)

// The sales card on first load: title, the four period buttons and the chart.
export const SalesChartSkeleton = ({ label }: { label: string }) => (
  <Card className={CARD}>
    <CardHeader className="flex flex-row items-center justify-between gap-4">
      <CardTitleSkeleton titleClassName="w-48" withSubtitle />
      <div className="flex items-center gap-1 rounded-full bg-muted/60 p-1">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} data-testid="skeleton-pill" className="h-8 w-14 rounded-full" />
        ))}
      </div>
    </CardHeader>
    <CardContent>
      <ChartSkeleton label={label} />
    </CardContent>
  </Card>
)

// Top products (2/3 wide) next to the by-category list.
export const ProductPerformanceSkeleton = ({ label }: { label: string }) => (
  <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
    <Card className={`${CARD} lg:col-span-2`}>
      <CardHeader>
        <CardTitleSkeleton titleClassName="w-56" withSubtitle />
      </CardHeader>
      <CardContent>
        <BarRowsSkeleton label={label} rows={5} />
      </CardContent>
    </Card>
    <Card className={CARD}>
      <CardHeader>
        <CardTitleSkeleton titleClassName="w-36" />
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {Array.from({ length: 5 }, (_, i) => (
          <div key={i} data-testid="skeleton-category-row" className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-2">
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-4 w-10" />
            </div>
            <Skeleton className="h-1.5 w-full rounded-full" />
          </div>
        ))}
      </CardContent>
    </Card>
  </div>
)

// Table analytics: the four headline figures and the revenue ranking.
export const TableAnalyticsSkeleton = ({ label }: { label: string }) => (
  <Card className={CARD}>
    <CardHeader>
      <CardTitleSkeleton titleClassName="w-48" />
    </CardHeader>
    <CardContent className="flex flex-col gap-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 sm:divide-x sm:divide-border/40 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} data-testid="skeleton-stat" className="flex flex-col gap-2 py-3 sm:px-6 sm:py-0 sm:first:pl-0">
            <Skeleton className="h-3 w-28" />
            <Skeleton className="h-8 w-16" />
          </div>
        ))}
      </div>
      <BarRowsSkeleton label={label} rows={5} />
    </CardContent>
  </Card>
)
