import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from './LoadingStatus'

// One block with the height of the real chart, so the card does not jump when the chart arrives.
export const ChartSkeleton = ({ label }: { label: string }) => (
  <>
    <LoadingStatus label={label} />
    <Skeleton data-testid="skeleton-chart" className="h-64 w-full rounded-xl" />
  </>
)
