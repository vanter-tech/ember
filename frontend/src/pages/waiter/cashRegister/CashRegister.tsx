import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { RotateCcw } from 'lucide-react'
import { OpenShiftDialog } from './components/OpenShiftDialog'
import { MovementDialog } from './components/MovementDialog'
import { RefundPaymentModal } from '@/pages/waiter/components/RefundPaymentModal'
import { SectionTour } from '@/components/tours/SectionTour'
import { useTranslation } from '@/lib/i18n'

export const CashRegister = () => {
  const { t } = useTranslation('waiter')
  const { openModal } = useUIStore()

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

  if (isLoading) {
    return <div className="p-6 text-zinc-500">{t('loadingCashRegister')}</div>
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">{t('cashRegisterTitle')}</h1>
        <p className="text-sm text-muted-foreground">{t('cashRegisterSubtitle')}</p>
      </div>

      <div id="waiter-cashregister-tour-content">
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
              <Badge variant="secondary">{shift.status === 'OPEN' ? t('shiftStatusOpen') : t('shiftStatusClosed')}</Badge>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div>
                  <p className="text-xs text-muted-foreground">{t('openingFloatLabel')}</p>
                  <p className="text-lg font-bold text-primary">{formatCurrency(shift.openingFloat ?? 0)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{t('openedByLabel')}</p>
                  <p className="text-sm font-medium">{shift.openedByName}</p>
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
                    <TableHead>{t('typeLabel')}</TableHead>
                    <TableHead>{t('amountLabel')}</TableHead>
                    <TableHead>{t('reasonLabel')}</TableHead>
                    <TableHead>{t('registeredByLabel')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(detail?.movements ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4} className="text-center text-sm text-muted-foreground">
                        {t('noMovementsRegistered')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    (detail!.movements ?? []).map((movement) => (
                      <TableRow key={movement.id}>
                        <TableCell>{movement.type === 'CASH_IN' ? t('cashInLabel') : t('cashOutLabel')}</TableCell>
                        <TableCell>{formatCurrency(movement.amount ?? 0)}</TableCell>
                        <TableCell>{movement.reason}</TableCell>
                        <TableCell>{movement.createdByName}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
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
                    <TableHead>{t('participantLabel')}</TableHead>
                    <TableHead>{t('amountLabel')}</TableHead>
                    <TableHead>{t('refundedLabel')}</TableHead>
                    <TableHead className="text-right">{t('actionLabel')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(detail?.payments ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4} className="text-center text-sm text-muted-foreground">
                        {t('noPaymentsRegistered')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    (detail!.payments ?? []).map((payment) => (
                      <TableRow key={payment.id}>
                        <TableCell>{payment.participantName}</TableCell>
                        <TableCell>{formatCurrency(payment.amount ?? 0)}</TableCell>
                        <TableCell>
                          {payment.refundedAmount && payment.refundedAmount > 0
                            ? formatCurrency(payment.refundedAmount)
                            : '—'}
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
            </CardContent>
          </Card>
        </>
      )}
      </div>

      <OpenShiftDialog />
      <MovementDialog />
      {/* CloseShiftDialog is mounted globally by CashShiftSentinel (WaiterLayout). */}
      <RefundPaymentModal />
      <SectionTour sectionId="waiter-cash-register" steps={tourSteps} />
    </div>
  )
}
