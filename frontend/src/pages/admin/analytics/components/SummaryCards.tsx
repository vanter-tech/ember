import { useQuery } from '@tanstack/react-query'
import { analyticsService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { DollarSign, Users, Receipt } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'

export const SummaryCards = () => {
  const { t } = useTranslation('admin')
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsSummary'],
    queryFn: () => analyticsService.getSummary(),
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-10 text-sm text-muted-foreground">
        {t('loadingSummaryMetrics')}
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="flex items-center justify-center py-10 text-sm text-destructive">
        {t('loadingSummaryMetricsError')}
      </div>
    )
  }

  const cards = [
    {
      label: t('totalRevenueLabel'),
      value: `$${(data.totalRevenue ?? 0).toFixed(2)}`,
      icon: DollarSign,
    },
    {
      label: t('activeSessionsLabel'),
      value: data.activeSessions ?? 0,
      icon: Users,
    },
    {
      label: t('averageOrderValueLabel'),
      value: `$${(data.averageOrderValue ?? 0).toFixed(2)}`,
      icon: Receipt,
    },
  ]

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
      {cards.map(({ label, value, icon: Icon }) => (
        <Card
          key={label}
          className="border border-border/40 bg-background py-6 shadow-sm"
        >
          <CardHeader className="flex flex-row items-center justify-start gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10">
              <Icon className="h-4 w-4 text-primary" strokeWidth={2} />
            </div>
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {label}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold tracking-tight tabular-nums text-primary">
              {value}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
