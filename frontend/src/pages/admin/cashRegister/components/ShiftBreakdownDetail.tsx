import { type CashShiftResponse } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import { NICARAGUA_DENOMINATIONS } from '@/lib/denominations'
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

/**
 * What was counted bill by bill when the shift opened and closed, plus the close notes. Renders nothing
 * when there is none, unless `emptyLabel` is given (a view whose expanded row would otherwise be blank).
 */
export const ShiftBreakdownDetail = ({ shift, emptyLabel }: { shift?: CashShiftResponse; emptyLabel?: string }) => {
  const { t } = useTranslation('admin')

  if (!shift) return null
  const opening = shift.openingBreakdown ?? []
  const closing = shift.closingBreakdown ?? []
  if (opening.length === 0 && closing.length === 0 && !shift.closeNotes) {
    return emptyLabel ? <p className="text-sm text-muted-foreground">{emptyLabel}</p> : null
  }

  const heading = 'text-xs font-semibold uppercase tracking-wider text-muted-foreground'
  return (
    <div className="mt-3 flex flex-col gap-2 border-t border-border/40 pt-3 text-sm">
      {opening.length > 0 && (
        <div>
          <p className={heading}>{t('openingBreakdownLabel')}</p>
          <p>{opening.map(denominationLabel).join(', ')}</p>
        </div>
      )}
      {closing.length > 0 && (
        <div>
          <p className={heading}>{t('closingBreakdownLabel')}</p>
          <p>{closing.map(denominationLabel).join(', ')}</p>
        </div>
      )}
      {shift.closeNotes && (
        <div>
          <p className={heading}>{t('closeNotesLabel')}</p>
          <p>{shift.closeNotes}</p>
        </div>
      )}
    </div>
  )
}
