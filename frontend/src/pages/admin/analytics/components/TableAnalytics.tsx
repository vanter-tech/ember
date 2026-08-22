import { useQuery } from '@tanstack/react-query'
import { LayoutGrid } from 'lucide-react'
import { analyticsService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useTranslation } from '@/lib/i18n'

export const TableAnalytics = () => {
  const { t } = useTranslation('admin')
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsTables'],
    queryFn: () => analyticsService.getTables(),
  })

  const tables = data?.tables ?? []
  const maxRevenue = Math.max(...tables.map((table) => table.revenue ?? 0), 0)

  return (
    <Card className="border border-border/40 bg-background py-6 shadow-sm">
      <CardHeader>
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
            <LayoutGrid className="h-4 w-4 text-primary" strokeWidth={2} />
          </div>
          <CardTitle className="text-base font-semibold tracking-tight text-foreground">
            {t('tableAnalyticsTitle')}
          </CardTitle>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {isLoading && (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            {t('loadingTables')}
          </div>
        )}
        {isError && (
          <div className="flex items-center justify-center py-16 text-sm text-destructive">
            {t('loadingTablesError')}
          </div>
        )}
        {data && (
          <div className="grid grid-cols-1 divide-y divide-border/40 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            <div className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:py-0 sm:px-6 sm:first:pl-0 sm:last:pr-0">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('activeTablesLabel')}
              </p>
              <p className="text-2xl font-bold tracking-tight tabular-nums text-primary">
                {data.activeTableCount ?? 0}
              </p>
            </div>
            <div className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:py-0 sm:px-6 sm:first:pl-0 sm:last:pr-0">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('averageTurnoverLabel')}
              </p>
              <p className="text-2xl font-bold tracking-tight tabular-nums text-primary">
                {(data.averageTurnoverRate ?? 0).toFixed(2)}
              </p>
            </div>
            <div className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:py-0 sm:px-6 sm:first:pl-0 sm:last:pr-0">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('averageDurationLabel')}
              </p>
              <p className="text-2xl font-bold tracking-tight tabular-nums text-primary">
                {(data.averageSessionDurationMinutes ?? 0).toFixed(0)}
              </p>
            </div>
          </div>
        )}
        {data && tables.length === 0 && (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            {t('noTableActivity')}
          </div>
        )}
        {tables.length > 0 && (
          <div className="flex flex-col divide-y divide-border/40">
            {tables.map((table, index) => (
              <div
                key={table.tableId ?? index}
                className="flex flex-col gap-1.5 py-3 first:pt-0 last:pb-0"
              >
                <div className="flex items-baseline justify-between gap-2 text-sm">
                  <span className="font-medium text-foreground">
                    {t('tableNumberLabel', { tableNumber: table.tableNumber ?? '—' })}
                  </span>
                  <span className="shrink-0 text-muted-foreground">
                    {t('tableRevenueSummary', {
                      revenue: (table.revenue ?? 0).toFixed(2),
                      turnoverCount: table.turnoverCount ?? 0,
                      duration: (table.averageSessionDurationMinutes ?? 0).toFixed(0),
                    })}
                  </span>
                </div>
                <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full rounded-full bg-primary"
                    style={{
                      width: `${
                        maxRevenue > 0 ? ((table.revenue ?? 0) / maxRevenue) * 100 : 0
                      }%`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
