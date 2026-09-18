import { Fragment, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService, type CashShiftResponse } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { NICARAGUA_DENOMINATIONS } from '@/lib/denominations'
import { PaginationControls } from '@/components/PaginationControls'
import { useTranslation } from '@/lib/i18n'

// The generated CashShiftResponse type has optional denominationId/quantity (OpenAPI schema),
// unlike lib/denominations.ts's own stricter DenominationCount — this is the shape actually
// coming off the wire, not the frontend's local grid-input type.
type BreakdownEntry = NonNullable<CashShiftResponse['openingBreakdown']>[number]

const denominationLabel = (count: BreakdownEntry): string => {
  const denomination = NICARAGUA_DENOMINATIONS.find((d) => d.id === count.denominationId)
  const value = denomination ? formatCurrency(denomination.value) : (count.denominationId ?? '')
  return `${value} × ${count.quantity ?? 0}`
}

export const ShiftHistoryTable = () => {
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const { t } = useTranslation('admin')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cashShiftHistory', page],
    queryFn: () => cashShiftService.history({ page, size: 20 }),
  })

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', expandedId],
    queryFn: () => cashShiftService.detail(expandedId!),
    enabled: expandedId !== null,
  })

  if (isLoading) {
    return <div className="p-6 text-sm text-muted-foreground">{t('loadingShifts')}</div>
  }

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
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-sm text-muted-foreground">
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
                    </TableRow>
                    {expandedId === shift.id && (
                      <TableRow key={`${shift.id}-detail`}>
                        <TableCell colSpan={7} className="bg-muted/30">
                          {!detail ? (
                            <div className="py-3 text-sm text-muted-foreground">{t('loadingPayments')}</div>
                          ) : (detail.payments ?? []).length === 0 ? (
                            <div className="py-3 text-sm text-muted-foreground">{t('noPaymentsInShift')}</div>
                          ) : (
                            <Table>
                              <TableHeader>
                                <TableRow>
                                  <TableHead>{t('paymentTableColumnLabel')}</TableHead>
                                  <TableHead>{t('paymentAmountColumnLabel')}</TableHead>
                                  <TableHead>{t('paymentMethodColumnLabel')}</TableHead>
                                  <TableHead>{t('statusColumnLabel')}</TableHead>
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
                                      <TableCell>
                                        {payment.tableNumber != null ? `#${payment.tableNumber}` : '—'}
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
                                      </TableCell>
                                      <TableCell>
                                        {isRefunded
                                          ? t('refundedLabel')
                                          : payment.status === 'CONFIRMED'
                                            ? t('paymentStatusConfirmedLabel')
                                            : t('paymentStatusPendingLabel')}
                                      </TableCell>
                                    </TableRow>
                                  )
                                })}
                              </TableBody>
                            </Table>
                          )}
                          {detail && ((detail.shift?.openingBreakdown?.length ?? 0) > 0 ||
                            (detail.shift?.closingBreakdown?.length ?? 0) > 0 ||
                            detail.shift?.closeNotes) && (
                            <div className="mt-3 flex flex-col gap-2 border-t border-border/40 pt-3 text-sm">
                              {(detail.shift?.openingBreakdown?.length ?? 0) > 0 && (
                                <div>
                                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                    {t('openingBreakdownLabel')}
                                  </p>
                                  <p>{detail.shift!.openingBreakdown!.map(denominationLabel).join(', ')}</p>
                                </div>
                              )}
                              {(detail.shift?.closingBreakdown?.length ?? 0) > 0 && (
                                <div>
                                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                    {t('closingBreakdownLabel')}
                                  </p>
                                  <p>{detail.shift!.closingBreakdown!.map(denominationLabel).join(', ')}</p>
                                </div>
                              )}
                              {detail.shift?.closeNotes && (
                                <div>
                                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                    {t('closeNotesLabel')}
                                  </p>
                                  <p>{detail.shift.closeNotes}</p>
                                </div>
                              )}
                            </div>
                          )}
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
