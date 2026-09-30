import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Banknote } from 'lucide-react'
import { cashDrawerService, type CashDrawerEvent } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/EmptyState'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { CASH_DRAWER_QUERY_KEY, useCashDrawerEvents } from './useCashDrawerEvents'

export const PendingCashList = () => {
  const { t } = useTranslation('waiter')
  const queryClient = useQueryClient()
  const { data: events = [] } = useCashDrawerEvents()

  const mutation = useMutation({
    mutationFn: (id: string) => cashDrawerService.receive(id),
    onSuccess: (event) => {
      queryClient.invalidateQueries({ queryKey: CASH_DRAWER_QUERY_KEY })
      if (event.drawer === 'FAILED') toast.error(t('drawerFailedToast'))
      else toast.success(t('drawerOpenedToast'))
    },
    onError: () => toast.error(t('drawerFailedToast')),
  })

  // Only sales still to accept: a drawer that failed after receiving is handled in ReceivedCashList so it never blocks the queue.
  const actionable = events.filter((e) => e.status === 'PENDING' && e.type === 'CASH_SALE')

  const row = (e: CashDrawerEvent) => (
    <Card key={e.id} className="py-4">
      <CardHeader>
        <CardTitle className="text-base">
          {e.type === 'MANUAL' ? (e.reason ?? '') : t('drawerTable', { table: e.tableNumber ?? '?' })}
        </CardTitle>
        <CardDescription>
          {new Date(e.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-1">
        {e.type !== 'MANUAL' && (
          <span className="text-4xl font-bold tracking-tight text-[#8c1717]">{formatCurrency(e.amount ?? 0)}</span>
        )}
        {e.createdByName && (
          <span className="text-sm text-muted-foreground">{t('drawerLaunchedBy', { name: e.createdByName })}</span>
        )}
      </CardContent>
      <CardFooter>
        <Button className="w-full" disabled={mutation.isPending} onClick={() => mutation.mutate(e.id)}>
          {t('drawerReceiveButton')}
        </Button>
      </CardFooter>
    </Card>
  )

  if (actionable.length === 0) return <EmptyState icon={Banknote} title={t('drawerPendingEmpty')} />

  return <div className="grid grid-cols-[repeat(auto-fill,minmax(18rem,1fr))] gap-4">{actionable.map(row)}</div>
}
