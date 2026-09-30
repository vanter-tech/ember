import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { cashDrawerService } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { CASH_DRAWER_QUERY_KEY, useCashDrawerEvents } from './useCashDrawerEvents'

const hhmm = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

/**
 * Cash sales already received in the current shift. A drawer that failed to open stays here as a
 * non-blocking warning (retry or skip) instead of going back to the pending queue, so a busy
 * shift keeps flowing.
 */
export const ReceivedCashList = () => {
  const { t } = useTranslation('waiter')
  const queryClient = useQueryClient()
  const { data: events = [] } = useCashDrawerEvents()

  const refresh = () => queryClient.invalidateQueries({ queryKey: CASH_DRAWER_QUERY_KEY })
  const retry = useMutation({
    mutationFn: (id: string) => cashDrawerService.receive(id),
    onSuccess: (event) => {
      refresh()
      if (event.drawer === 'FAILED') toast.error(t('drawerFailedToast'))
      else toast.success(t('drawerOpenedToast'))
    },
    onError: () => toast.error(t('drawerFailedToast')),
  })
  const skip = useMutation({
    mutationFn: (id: string) => cashDrawerService.skip(id),
    onSuccess: () => {
      refresh()
      toast.success(t('drawerSkippedToast'))
    },
    onError: () => toast.error(t('drawerFailedToast')),
  })

  const sales = events.filter((e) => e.status === 'RECEIVED' && e.type === 'CASH_SALE')
  const failed = sales.filter((e) => e.drawer === 'FAILED')
  const received = sales.filter((e) => e.drawer !== 'FAILED')
  const busy = retry.isPending || skip.isPending

  return (
    <>
      {failed.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground">
            <AlertTriangle size={18} className="text-amber-600" /> {t('drawerAttentionTitle')}
          </h2>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">
            {failed.map((e) => (
              <Card key={e.id} className="border border-amber-300 py-4">
                <CardHeader>
                  <CardTitle className="text-base">
                    {t('drawerTable', { table: e.tableNumber ?? '?' })} · {formatCurrency(e.amount ?? 0)}
                  </CardTitle>
                  <CardDescription>{t('drawerFailedTitle')}</CardDescription>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">
                  {e.drawerError ?? t('drawerFailedGeneric')}
                </CardContent>
                <CardFooter className="flex-col gap-2">
                  <Button className="w-full" disabled={busy} onClick={() => retry.mutate(e.id)}>
                    {t('drawerRetryButton')}
                  </Button>
                  <Button variant="outline" className="w-full" disabled={busy} onClick={() => skip.mutate(e.id)}>
                    {t('drawerSkipButton')}
                  </Button>
                </CardFooter>
              </Card>
            ))}
          </div>
        </section>
      )}
      {received.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground">
            <CheckCircle2 size={18} className="text-emerald-600" /> {t('drawerReceivedTitle')}
          </h2>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">
            {received.map((e) => (
              <Card key={e.id} size="sm">
                <CardContent className="flex items-center justify-between gap-3">
                  <div className="flex flex-col">
                    <span className="font-medium text-foreground">
                      {t('drawerTable', { table: e.tableNumber ?? '?' })}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {[
                        e.createdByName && t('drawerLaunchedBy', { name: e.createdByName }),
                        e.receivedAt && t('drawerReceivedAt', { time: hhmm(e.receivedAt) }),
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </span>
                  </div>
                  <span className="text-lg font-bold text-foreground">{formatCurrency(e.amount ?? 0)}</span>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      )}
    </>
  )
}
