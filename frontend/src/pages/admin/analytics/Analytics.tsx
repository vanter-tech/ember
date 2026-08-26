import { useTranslation } from '@/lib/i18n'
import { SectionTour } from '@/components/tours/SectionTour'
import { SummaryCards } from './components/SummaryCards'
import { SalesChart } from './components/SalesChart'
import { ProductPerformance } from './components/ProductPerformance'
import { TableAnalytics } from './components/TableAnalytics'

export const Analytics = () => {
  const { t } = useTranslation('admin')

  const tourSteps = [
    {
      target: '#analytics-tour-summary',
      title: t('tourAnalyticsSummaryTitle'),
      content: t('tourAnalyticsSummaryContent'),
      skipBeacon: true,
    },
    {
      target: '#analytics-tour-sales',
      title: t('tourAnalyticsSalesTitle'),
      content: t('tourAnalyticsSalesContent'),
    },
    {
      target: '#analytics-tour-products',
      title: t('tourAnalyticsProductsTitle'),
      content: t('tourAnalyticsProductsContent'),
    },
    {
      target: '#analytics-tour-tables',
      title: t('tourAnalyticsTablesTitle'),
      content: t('tourAnalyticsTablesContent'),
    },
  ]

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

      <div id="analytics-tour-summary">
        <SummaryCards />
      </div>
      <div id="analytics-tour-sales">
        <SalesChart />
      </div>
      <div id="analytics-tour-products">
        <ProductPerformance />
      </div>
      <div id="analytics-tour-tables">
        <TableAnalytics />
      </div>
      <SectionTour sectionId="admin-analytics" steps={tourSteps} />
    </div>
  )
}
