import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { billCode } from '@/lib/documentCodes'
import { formatCurrency, formatDateTime } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { ShiftAuditSkeleton } from './CashRegisterSkeletons'

const HEADING = 'text-xs font-semibold uppercase tracking-wider text-muted-foreground'

/**
 * The audit trail of one shift: every refund of its payments (why, who, when) and every bill voided
 * while it was open. Shares the `cashShiftDetail` query with the history table, so expanding a row
 * there and here costs one request.
 */
export const ShiftAuditDetail = ({ shiftId }: { shiftId: number }) => {
  const { t } = useTranslation('admin')
  const { data, isLoading } = useQuery({
    queryKey: ['cashShiftDetail', shiftId],
    queryFn: () => cashShiftService.detail(shiftId),
  })

  if (isLoading) return <ShiftAuditSkeleton label={t('loadingShiftAudit')} />
  if (!data) return null

  const refunds = data.refunds ?? []
  const voided = data.voidedBills ?? []

  return (
    <div className="mt-3 flex flex-col gap-4 border-t border-border/40 pt-3 text-sm">
      <div className="flex flex-col gap-2">
        <p className={HEADING}>{t('shiftRefundsTitle')}</p>
        {refunds.length === 0 ? (
          <p className="text-muted-foreground">{t('noShiftRefunds')}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('auditBillColumnLabel')}</TableHead>
                <TableHead>{t('auditParticipantColumnLabel')}</TableHead>
                <TableHead>{t('auditAmountColumnLabel')}</TableHead>
                <TableHead>{t('auditReasonColumnLabel')}</TableHead>
                <TableHead>{t('auditRefundedByColumnLabel')}</TableHead>
                <TableHead>{t('auditTimeColumnLabel')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {refunds.map((refund) => (
                <TableRow key={refund.id}>
                  <TableCell>{billCode(refund.billCode, refund.billId)}</TableCell>
                  <TableCell>{refund.participantName}</TableCell>
                  <TableCell>{formatCurrency(refund.amount ?? 0)}</TableCell>
                  <TableCell className="max-w-64 whitespace-normal">{refund.reason}</TableCell>
                  <TableCell>{refund.refundedByName}</TableCell>
                  <TableCell>{formatDateTime(refund.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <p className={HEADING}>{t('shiftVoidsTitle')}</p>
        {voided.length === 0 ? (
          <p className="text-muted-foreground">{t('noShiftVoids')}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('auditBillColumnLabel')}</TableHead>
                <TableHead>{t('auditTableColumnLabel')}</TableHead>
                <TableHead>{t('auditTotalColumnLabel')}</TableHead>
                <TableHead>{t('auditReasonColumnLabel')}</TableHead>
                <TableHead>{t('auditVoidedByColumnLabel')}</TableHead>
                <TableHead>{t('auditTimeColumnLabel')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {voided.map((bill) => (
                <TableRow key={bill.id}>
                  <TableCell>{billCode(bill.billCode, bill.id)}</TableCell>
                  <TableCell>{bill.tableLabel ?? (bill.tableNumber != null ? `#${bill.tableNumber}` : '—')}</TableCell>
                  <TableCell>{formatCurrency(bill.total ?? 0)}</TableCell>
                  <TableCell className="max-w-64 whitespace-normal">{bill.voidReason}</TableCell>
                  <TableCell>{bill.voidedByName}</TableCell>
                  <TableCell>{formatDateTime(bill.voidedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  )
}
