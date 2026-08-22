import { useTranslation } from '@/lib/i18n'
import { SummaryCards } from './components/SummaryCards'
import { SalesChart } from './components/SalesChart'
import { ProductPerformance } from './components/ProductPerformance'
import { TableAnalytics } from './components/TableAnalytics'

export const Analytics = () => {
  const { t } = useTranslation('admin')

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          {t('analyticsPageTitle')}
        </h1>
        <p className="text-sm text-muted-foreground">
          {t('analyticsPageSubtitle')}
        </p>
      </div>

      <SummaryCards />
      <SalesChart />
      <ProductPerformance />
      <TableAnalytics />
    </div>
  )
}
