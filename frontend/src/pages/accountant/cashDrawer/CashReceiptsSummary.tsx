import { Card, CardContent } from '@/components/ui/card'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { useCashDrawerEvents } from './useCashDrawerEvents'

/** Headline numbers for the receipts view: how many cash sales wait for the accountant and how much. */
export const CashReceiptsSummary = () => {
  const { t } = useTranslation('waiter')
  const { data: events = [] } = useCashDrawerEvents()
  const pending = events.filter((e) => e.status === 'PENDING' && e.type === 'CASH_SALE')
  const total = pending.reduce((sum, e) => sum + (e.amount ?? 0), 0)

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <Card className="py-4">
        <CardContent className="flex flex-col gap-1">
          <span className="text-sm text-muted-foreground">{t('drawerSummaryPending')}</span>
          <span className="text-4xl font-bold tracking-tight text-foreground">{pending.length}</span>
        </CardContent>
      </Card>
      <Card className="py-4">
        <CardContent className="flex flex-col gap-1">
          <span className="text-sm text-muted-foreground">{t('drawerSummaryTotal')}</span>
          <span className="text-4xl font-bold tracking-tight text-[#8c1717]">{formatCurrency(total)}</span>
        </CardContent>
      </Card>
    </div>
  )
}
