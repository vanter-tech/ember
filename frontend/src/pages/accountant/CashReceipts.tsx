import { Banknote } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'
import { PendingCashList } from './cashDrawer/PendingCashList'
import { CashReceiptsSummary } from './cashDrawer/CashReceiptsSummary'
import { ReceivedCashList } from './cashDrawer/ReceivedCashList'

/** Own view for the cash payments a waiter confirmed and the accountant has yet to receive. */
export const CashReceipts = () => {
  const { t } = useTranslation('waiter')

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="flex items-center gap-3 text-3xl font-bold tracking-tight text-foreground">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[#8c1717] text-white">
            <Banknote size={22} />
          </span>
          {t('cashReceiptsTitle')}
        </h1>
        <p className="text-sm text-muted-foreground">{t('cashReceiptsSubtitle')}</p>
      </div>
      <CashReceiptsSummary />
      <PendingCashList />
      <ReceivedCashList />
    </div>
  )
}
