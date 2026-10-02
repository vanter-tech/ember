import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

// One of the two headline tiles (label + big number) of the receipts summary.
export const SummaryTileSkeleton = () => (
  <Card data-testid="skeleton-summary-tile" className="py-4">
    <CardContent className="flex flex-col gap-3">
      <Skeleton className="h-4 w-32" />
      <Skeleton className="h-10 w-28" />
    </CardContent>
  </Card>
)

// A pending cash payment card: table + time, the big amount, who launched it, and its button.
export const ReceiptCardSkeleton = () => (
  <Card data-testid="skeleton-receipt-card" className="py-4">
    <CardHeader className="gap-2">
      <Skeleton className="h-5 w-24" />
      <Skeleton className="h-3 w-12" />
    </CardHeader>
    <CardContent className="flex flex-col gap-3">
      <Skeleton className="h-10 w-32" />
      <Skeleton className="h-3 w-36" />
    </CardContent>
    <CardFooter>
      <Skeleton className="h-10 w-full" />
    </CardFooter>
  </Card>
)
