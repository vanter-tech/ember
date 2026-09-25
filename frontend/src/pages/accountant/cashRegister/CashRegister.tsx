import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { RotateCcw, Clock } from 'lucide-react'
import { OpenShiftDialog } from './components/OpenShiftDialog'
import { MovementDialog } from './components/MovementDialog'
import { RefundPaymentModal } from '@/pages/waiter/components/RefundPaymentModal'
import { SectionTour } from '@/components/tours/SectionTour'
import { useTranslation } from '@/lib/i18n'

export const CashRegister = () => {
  const { t } = useTranslation('waiter')
  const { openModal } = useUIStore()
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(id)
  }, [])

  const tourSteps = [
    {
      target: '#waiter-cashregister-tour-content',
      title: t('tourCashRegisterTitle'),
      content: t('tourCashRegisterContent'),
      skipBeacon: true,
    },
  ]

  const { data: shift, isLoading } = useQuery({
    queryKey: ['cashShiftCurrent'],
    queryFn: cashShiftService.current,
    refetchInterval: 60_000,
  })

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', shift?.id],
    queryFn: () => cashShiftService.detail(shift!.id!),
    enabled: !!shift?.id,
  })

  const fmtTime = (iso?: string) =>
    iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'
  const fmtDuration = (ms: number) => {
    const totalMin = Math.max(0, Math.floor(ms / 60000))
    const h = Math.floor(totalMin / 60)
    return h > 0 ? `${h}h ${totalMin % 60}m` : `${totalMin}m`
  }

  const movements = detail?.movements ?? []
  const payments = detail?.payments ?? []
  const confirmed = payments.filter((p) => p.status !== 'PENDING')
  // Same formula the backend uses when closing the shift (gross confirmed cash, refunds not netted).
  const cashSales = confirmed.filter((p) => p.method === 'PHYSICAL').reduce((s, p) => s + (p.amount ?? 0), 0)
  const cashIn = movements.filter((m) => m.type === 'CASH_IN').reduce((s, m) => s + (m.amount ?? 0), 0)
  const cashOut = movements.filter((m) => m.type !== 'CASH_IN').reduce((s, m) => s + (m.amount ?? 0), 0)
  const refundedTotal = payments.reduce((s, p) => s + (p.refundedAmount ?? 0), 0)
  const expectedCash = (shift?.openingFloat ?? 0) + cashSales + cashIn - cashOut
  const msLeft = shift?.effectiveDeadline ? new Date(shift.effectiveDeadline).getTime() - now : null

  if (isLoading) {
    return <div className="p-6 text-zinc-500">{t('loadingCashRegister')}</div>
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">{t('cashRegisterTitle')}</h1>
        <p className="text-sm text-muted-foreground">{t('cashRegisterSubtitle')}</p>
      </div>

      <div id="waiter-cashregister-tour-content" className="flex flex-col gap-6">
      {!shift ? (
        <Card className="border border-border/40 bg-background py-6 shadow-sm">
          <CardContent className="flex flex-col items-center gap-4 py-10">
            <p className="text-sm text-muted-foreground">{t('noOpenShift')}</p>
            <Button onClick={() => openModal('OPEN_SHIFT')}>{t('openCajaButton')}</Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('shiftNumberLabel', { number: shift.shiftNumber ?? '' })}
              </CardTitle>
              <div className="flex items-center gap-2">
                {shift.overdue && <Badge variant="destructive">{t('shiftOverdueBadge')}</Badge>}
                <Badge variant="secondary">{shift.status === 'OPEN' ? t('shiftStatusOpen') : t('shiftStatusClosed')}</Badge>
              </div>
            </CardHeader>
            <CardContent className="flex flex-col gap-6">
              <div className="flex flex-wrap items-center gap-x-6 gap-y-1 text-sm text-muted-foreground">
                <span>
                  {t('openedByLabel')}: <span className="font-medium text-foreground">{shift.openedByName}</span>
                </span>
                <span>
                  {t('openedAtLabel')}: <span className="font-medium text-foreground">{fmtTime(shift.openedAt)}</span>
                </span>
                {shift.effectiveDeadline && (
                  <span className="flex items-center gap-1">
                    <Clock className="size-3.5" />
                    {t('deadlineLabel')}: <span className="font-medium text-foreground">{fmtTime(shift.effectiveDeadline)}</span>
                    {msLeft !== null && msLeft > 0 && (
                      <span>({t('timeLeftLabel', { time: fmtDuration(msLeft) })})</span>
                    )}
                  </span>
                )}
              </div>
              <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
                <div className="rounded-2xl bg-zinc-50 p-4">
                  <p className="text-xs text-muted-foreground">{t('openingFloatLabel')}</p>
                  <p className="text-lg font-bold">{formatCurrency(shift.openingFloat ?? 0)}</p>
                </div>
                <div className="rounded-2xl bg-zinc-50 p-4">
                  <p className="text-xs text-muted-foreground">{t('cashSalesLabel')}</p>
                  <p className="text-lg font-bold">{formatCurrency(cashSales)}</p>
                </div>
                <div className="rounded-2xl bg-zinc-50 p-4">
                  <p className="text-xs text-muted-foreground">
                    {t('cashInTotalLabel')} / {t('cashOutTotalLabel')}
                  </p>
                  <p className="text-lg font-bold">
                    <span className="text-emerald-700">+{formatCurrency(cashIn)}</span>{' '}
                    <span className="text-red-700">−{formatCurrency(cashOut)}</span>
                  </p>
                </div>
                <div className="col-span-2 rounded-2xl bg-primary/10 p-4 lg:col-span-1">
                  <p className="text-xs text-muted-foreground">{t('expectedCashLabel')}</p>
                  <p className="text-xl font-bold text-primary">{formatCurrency(expectedCash)}</p>
                  <p className="mt-1 text-[11px] text-muted-foreground">{t('expectedCashHint')}</p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  disabled={shift.overdue ?? false}
                  title={shift.overdue ? t('cashShiftOverdueMovementBlocked') : undefined}
                  onClick={() => openModal('CASH_MOVEMENT', { shiftId: shift.id })}
                >
                  {t('recordMovementButton')}
                </Button>
                <Button onClick={() => openModal('CLOSE_SHIFT', { shiftId: shift.id })}>
                  {t('closeCajaButton')}
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader>
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('movementsTitle')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('timeColumnLabel')}</TableHead>
                    <TableHead>{t('typeLabel')}</TableHead>
                    <TableHead>{t('amountLabel')}</TableHead>
                    <TableHead>{t('reasonLabel')}</TableHead>
                    <TableHead>{t('registeredByLabel')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(detail?.movements ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5} className="text-center text-sm text-muted-foreground">
                        {t('noMovementsRegistered')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    (detail!.movements ?? []).map((movement) => (
                      <TableRow key={movement.id}>
                        <TableCell>{fmtTime(movement.createdAt)}</TableCell>
                        <TableCell>{movement.type === 'CASH_IN' ? t('cashInLabel') : t('cashOutLabel')}</TableCell>
                        <TableCell>{formatCurrency(movement.amount ?? 0)}</TableCell>
                        <TableCell>{movement.reason}</TableCell>
                        <TableCell>{movement.createdByName}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
              {movements.length > 0 && (
                <p className="mt-3 text-right text-sm text-muted-foreground">
                  {t('cashInTotalLabel')}: <span className="font-medium text-emerald-700">{formatCurrency(cashIn)}</span>
                  {' · '}
                  {t('cashOutTotalLabel')}: <span className="font-medium text-red-700">{formatCurrency(cashOut)}</span>
                </p>
              )}
            </CardContent>
          </Card>

          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader>
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('paymentsTitle')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('timeColumnLabel')}</TableHead>
                    <TableHead>{t('tableColumnLabel')}</TableHead>
                    <TableHead>{t('participantLabel')}</TableHead>
                    <TableHead>{t('methodLabel')}</TableHead>
                    <TableHead>{t('statusLabel')}</TableHead>
                    <TableHead>{t('amountLabel')}</TableHead>
                    <TableHead>{t('refundedLabel')}</TableHead>
                    <TableHead className="text-right">{t('actionLabel')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(detail?.payments ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center text-sm text-muted-foreground">
                        {t('noPaymentsRegistered')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    (detail!.payments ?? []).map((payment) => (
                      <TableRow key={payment.id}>
                        <TableCell>{fmtTime(payment.createdAt)}</TableCell>
                        <TableCell>{payment.tableNumber ?? '—'}</TableCell>
                        <TableCell>{payment.participantName}</TableCell>
                        <TableCell>
                          <Badge variant="outline">
                            {payment.method === 'PHYSICAL' ? t('methodCash') : t('methodDigital')}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={payment.status === 'PENDING' ? 'secondary' : 'default'}>
                            {payment.status === 'PENDING' ? t('paymentPending') : t('paymentConfirmed')}
                          </Badge>
                        </TableCell>
                        <TableCell>{formatCurrency(payment.amount ?? 0)}</TableCell>
                        <TableCell>
                          {payment.refundedAmount && payment.refundedAmount > 0 ? (
                            <span className="font-medium text-red-700">{formatCurrency(payment.refundedAmount)}</span>
                          ) : (
                            '—'
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={!payment.remaining || payment.remaining <= 0}
                            onClick={() =>
                              openModal('REFUND_PAYMENT', {
                                billId: payment.billId,
                                participantName: payment.participantName,
                                paymentId: payment.id,
                              })
                            }
                          >
                            <RotateCcw className="w-4 h-4 mr-1" /> {t('refundButton')}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
              {payments.length > 0 && (
                <p className="mt-3 text-right text-sm text-muted-foreground">
                  {t('totalLabel')}: <span className="font-medium text-foreground">{formatCurrency(cashSales)}</span>
                  {refundedTotal > 0 && (
                    <>
                      {' · '}
                      {t('refundedTotalLabel')}: <span className="font-medium text-red-700">{formatCurrency(refundedTotal)}</span>
                    </>
                  )}
                </p>
              )}
            </CardContent>
          </Card>
        </>
      )}
      </div>

      <OpenShiftDialog />
      <MovementDialog />
      {/* CloseShiftDialog is mounted globally by CashShiftSentinel (AccountantLayout). */}
      <RefundPaymentModal />
      <SectionTour sectionId="waiter-cash-register" steps={tourSteps} />
    </div>
  )
}
