import { Fragment, useState } from 'react'
import { ShiftHistorySkeleton } from './CashRegisterSkeletons'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'
import { TableSkeleton } from '@/components/skeletons/TableSkeleton'
import { useShiftHistory } from './useShiftHistory'
import { ShiftBreakdownDetail } from './ShiftBreakdownDetail'
import { ShiftAuditDetail } from './ShiftAuditDetail'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency, formatDateTime } from '@/lib/format'
import { PaginationControls } from '@/components/PaginationControls'
import { useTranslation } from '@/lib/i18n'
import { billCode } from '@/lib/documentCodes'

export const ShiftHistoryTable = () => {
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const { t } = useTranslation('admin')

  const { data, isLoading, isError } = useShiftHistory(page)

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', expandedId],
    queryFn: () => cashShiftService.detail(expandedId!),
    enabled: expandedId !== null,
  })

  if (isLoading) return <ShiftHistorySkeleton label={t('loadingShifts')} />

  if (isError || !data) {
    return <div className="p-6 text-sm text-destructive">{t('loadingShiftsError')}</div>
  }

  return (
    <>
      <Card className="border border-border/40 bg-background py-6 shadow-sm">
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('shiftColumnLabel')}</TableHead>
                <TableHead>{t('statusColumnLabel')}</TableHead>
                <TableHead>{t('openedByColumnLabel')}</TableHead>
                <TableHead>{t('closedByColumnLabel')}</TableHead>
                <TableHead>{t('expectedColumnLabel')}</TableHead>
                <TableHead>{t('countedColumnLabel')}</TableHead>
                <TableHead>{t('varianceColumnLabel')}</TableHead>
                <TableHead>{t('closedAtColumnLabel')}</TableHead>
                <TableHead>{t('prolongCountColumnLabel')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} className="text-center text-sm text-muted-foreground">
                    {t('noShiftsRegistered')}
                  </TableCell>
                </TableRow>
              ) : (
                data.content.map((shift) => (
                  <Fragment key={shift.id}>
                    <TableRow
                      className="cursor-pointer"
                      onClick={() => setExpandedId(expandedId === shift.id ? null : shift.id!)}
                    >
                      <TableCell>#{shift.shiftNumber}</TableCell>
                      <TableCell>
                        <Badge variant={shift.status === 'OPEN' ? 'default' : 'secondary'}>
                          {shift.status === 'OPEN' ? t('openStatus') : t('closedStatus')}
                        </Badge>
                      </TableCell>
                      <TableCell>{shift.openedByName}</TableCell>
                      <TableCell>{shift.closedByName ?? '—'}</TableCell>
                      <TableCell>{shift.expectedCash != null ? formatCurrency(shift.expectedCash) : '—'}</TableCell>
                      <TableCell>{shift.countedCash != null ? formatCurrency(shift.countedCash) : '—'}</TableCell>
                      <TableCell>{shift.variance != null ? formatCurrency(shift.variance) : '—'}</TableCell>
                      <TableCell>{formatDateTime(shift.closedAt)}</TableCell>
                      <TableCell>{shift.prolongCount ?? 0}</TableCell>
                    </TableRow>
                    {expandedId === shift.id && (
                      <TableRow key={`${shift.id}-detail`}>
                        <TableCell colSpan={9} className="bg-muted/30">
                          {!detail ? (
                            <>
                              <LoadingStatus label={t('loadingPayments')} />
                              <TableSkeleton columns={5} rows={2} />
                            </>
                          ) : (detail.payments ?? []).length === 0 ? (
                            <div className="py-3 text-sm text-muted-foreground">{t('noPaymentsInShift')}</div>
                          ) : (
                            <Table>
                              <TableHeader>
                                <TableRow>
                                  <TableHead>{t('paymentBillColumnLabel')}</TableHead>
                                  <TableHead>{t('paymentTableColumnLabel')}</TableHead>
                                  <TableHead>{t('paymentAmountColumnLabel')}</TableHead>
                                  <TableHead>{t('paymentMethodColumnLabel')}</TableHead>
                                  <TableHead>{t('statusColumnLabel')}</TableHead>
                                  <TableHead>{t('paymentProcessedByColumnLabel')}</TableHead>
                                </TableRow>
                              </TableHeader>
                              <TableBody>
                                {/* ADMIN oversees billing here but never executes it — refunds are
                                    WAITER-only (POST /billing/payments/{id}/refund is
                                    @PreAuthorize("hasRole('WAITER')")), so this table is read-only.
                                    A WAITER refunds from the waiter cash-register page instead. */}
                                {(detail.payments ?? []).map((payment) => {
                                  const isRefunded = !payment.remaining || payment.remaining <= 0
                                  return (
                                    <TableRow key={payment.id}>
                                      <TableCell>{billCode(payment.billCode, payment.billId)}</TableCell>
                                      <TableCell>
                                        {payment.tableLabel ?? (payment.tableNumber != null ? `#${payment.tableNumber}` : '—')}
                                      </TableCell>
                                      <TableCell>
                                        {formatCurrency(payment.amount ?? 0)}
                                        {payment.refundedAmount && payment.refundedAmount > 0
                                          ? t('refundedAmountSuffix', { amount: formatCurrency(payment.refundedAmount) })
                                          : ''}
                                      </TableCell>
                                      <TableCell>
                                        {payment.method === 'DIGITAL'
                                          ? t('paymentMethodDigitalLabel')
                                          : t('paymentMethodPhysicalLabel')}
                                        {payment.method === 'DIGITAL' && payment.gatewayRef && (
                                          <span className="block text-xs text-muted-foreground">{payment.gatewayRef}</span>
                                        )}
                                      </TableCell>
                                      <TableCell>
                                        {isRefunded
                                          ? t('refundedLabel')
                                          : payment.status === 'CONFIRMED'
                                            ? t('paymentStatusConfirmedLabel')
                                            : t('paymentStatusPendingLabel')}
                                      </TableCell>
                                      <TableCell>{payment.processedByName ?? '—'}</TableCell>
                                    </TableRow>
                                  )
                                })}
                              </TableBody>
                            </Table>
                          )}
                          <ShiftBreakdownDetail shift={detail?.shift} />
                          {detail && <ShiftAuditDetail shiftId={shift.id!} />}
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <PaginationControls page={page} totalPages={data.totalPages} onPageChange={setPage} />
    </>
  )
}
