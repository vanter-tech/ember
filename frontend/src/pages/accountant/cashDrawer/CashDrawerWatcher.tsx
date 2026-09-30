import { useEffect, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { beep } from './beep'
import { ManualOpenDialog } from './ManualOpenDialog'
import { useCashDrawerEvents } from './useCashDrawerEvents'
import { useDrawerShortcut } from './useDrawerShortcut'

/** Mounted once in AccountantLayout: alerts on new pending receipts (any accountant page) and hosts F9. */
export const CashDrawerWatcher = () => {
  const { t } = useTranslation('waiter')
  const { data } = useCashDrawerEvents()
  const [manualOpen, setManualOpen] = useState(false)
  const seen = useRef<Set<string> | null>(null)

  useEffect(() => {
    if (!data) return
    const pending = data.filter((e) => e.status === 'PENDING')
    if (seen.current === null) {
      // The backlog is already visible in the list; do not beep for it.
      seen.current = new Set(pending.map((e) => e.id))
      return
    }
    const known = seen.current
    const fresh = pending.filter((e) => !known.has(e.id))
    if (fresh.length === 0) return
    fresh.forEach((e) => known.add(e.id))
    beep()
    fresh.forEach((e) =>
      toast(t('drawerNewPendingToast', { table: e.tableNumber ?? '?', amount: formatCurrency(e.amount ?? 0) })),
    )
  }, [data, t])

  useDrawerShortcut(() => setManualOpen(true))

  return <ManualOpenDialog open={manualOpen} onOpenChange={setManualOpen} />
}
