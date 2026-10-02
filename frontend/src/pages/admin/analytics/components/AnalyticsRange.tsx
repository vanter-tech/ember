import { useQuery } from '@tanstack/react-query'
import { analyticsService } from '@/lib/api'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'
import { useTranslation } from '@/lib/i18n'

/** The span of billing activity the figures below are drawn from, under the page subtitle. */
export const AnalyticsRange = () => {
  const { t, locale } = useTranslation('admin')
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsRange'],
    queryFn: () => analyticsService.getRange(),
  })

  if (isLoading) {
    return (
      <div data-testid="skeleton-range">
        <LoadingStatus label={t('loadingAnalyticsRange')} />
        <Skeleton className="h-4 w-72" />
      </div>
    )
  }
  if (isError || !data) return null

  if (!data.firstBillAt || !data.lastBillAt) {
    return <p className="text-xs text-muted-foreground">{t('analyticsRangeEmpty')}</p>
  }

  const day = (iso: string) =>
    new Date(iso).toLocaleDateString(locale === 'en' ? 'en-US' : 'es-MX', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    })

  return (
    <p className="text-xs text-muted-foreground">
      {t('analyticsRangeSummary', {
        from: day(data.firstBillAt),
        to: day(data.lastBillAt),
        count: data.billCount ?? 0,
      })}
    </p>
  )
}
