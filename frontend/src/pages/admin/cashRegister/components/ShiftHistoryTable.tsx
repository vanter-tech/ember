import { Fragment, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { PaginationControls } from '@/components/PaginationControls'

export const ShiftHistoryTable = () => {
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)

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
    return <div className="p-6 text-sm text-muted-foreground">Cargando turnos...</div>
  }

  if (isError || !data) {
    return <div className="p-6 text-sm text-destructive">Error al cargar el historial.</div>
  }

  return (
    <>
      <Card className="border border-border/40 bg-background py-6 shadow-sm">
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Turno</TableHead>
                <TableHead>Estado</TableHead>
                <TableHead>Abierto por</TableHead>
                <TableHead>Cerrado por</TableHead>
                <TableHead>Esperado</TableHead>
                <TableHead>Contado</TableHead>
                <TableHead>Diferencia</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-sm text-muted-foreground">
                    Sin turnos registrados.
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
                          {shift.status === 'OPEN' ? 'Abierto' : 'Cerrado'}
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
                            <div className="py-3 text-sm text-muted-foreground">Cargando pagos...</div>
                          ) : (detail.payments ?? []).length === 0 ? (
                            <div className="py-3 text-sm text-muted-foreground">Sin pagos en este turno.</div>
                          ) : (
                            <div className="flex flex-col gap-2 py-2">
                              {(detail.payments ?? []).map((payment) => (
                                <div
                                  key={payment.id}
                                  className="flex items-center justify-between px-2 py-1"
                                >
                                  <span className="text-sm">
                                    {payment.participantName} — {formatCurrency(payment.amount ?? 0)}
                                    {payment.refundedAmount && payment.refundedAmount > 0
                                      ? ` (reembolsado ${formatCurrency(payment.refundedAmount)})`
                                      : ''}
                                  </span>
                                  {/* ADMIN oversees billing here but never executes it — refunds are
                                      WAITER-only (POST /billing/payments/{id}/refund is
                                      @PreAuthorize("hasRole('WAITER')")), so this row is read-only.
                                      A WAITER refunds from the waiter cash-register page instead. */}
                                  <span className="text-xs text-muted-foreground">
                                    {!payment.remaining || payment.remaining <= 0 ? 'Reembolsado' : '—'}
                                  </span>
                                </div>
                              ))}
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
