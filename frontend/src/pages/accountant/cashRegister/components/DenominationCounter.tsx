import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { formatCurrency } from '@/lib/format'
import { NICARAGUA_DENOMINATIONS, sumBreakdown, type Denomination, type DenominationCount } from '@/lib/denominations'
import { useTranslation } from '@/lib/i18n'

interface DenominationCounterProps {
  onChange: (breakdown: DenominationCount[], total: number) => void
}

const toBreakdown = (quantities: Record<string, number>): DenominationCount[] =>
  Object.entries(quantities)
    .filter(([, quantity]) => quantity > 0)
    .map(([denominationId, quantity]) => ({ denominationId, quantity }))

export const DenominationCounter = ({ onChange }: DenominationCounterProps) => {
  const { t } = useTranslation('waiter')
  const [quantities, setQuantities] = useState<Record<string, number>>({})

  const handleQuantityChange = (denominationId: string, rawValue: string) => {
    const quantity = Math.max(0, Math.floor(Number(rawValue) || 0))
    const next = { ...quantities, [denominationId]: quantity }
    setQuantities(next)
    const breakdown = toBreakdown(next)
    onChange(breakdown, sumBreakdown(breakdown))
  }

  const rowLabel = (denomination: Denomination) => {
    if (denomination.kind === 'BILL') {
      return t('billDenominationLabel', { amount: formatCurrency(denomination.value) })
    }
    return t('coinDenominationLabel', { amount: formatCurrency(denomination.value) })
  }

  const renderRow = (denomination: Denomination) => (
    <div key={denomination.id} className="flex items-center justify-between gap-3">
      <label htmlFor={`denom-${denomination.id}`} className="text-sm font-medium">
        {formatCurrency(denomination.value)}
      </label>
      <Input
        id={`denom-${denomination.id}`}
        aria-label={rowLabel(denomination)}
        type="number"
        min="0"
        step="1"
        className="w-20 rounded-xl"
        value={quantities[denomination.id] ?? ''}
        onChange={(e) => handleQuantityChange(denomination.id, e.target.value)}
      />
    </div>
  )

  const bills = NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'BILL')
  const coins = NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'COIN')
  const total = sumBreakdown(toBreakdown(quantities))

  return (
    <div className="flex flex-col gap-4">
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {t('billsLabel')}
        </p>
        <div className="grid grid-cols-2 gap-2">{bills.map(renderRow)}</div>
      </div>
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {t('coinsLabel')}
        </p>
        <div className="grid grid-cols-2 gap-2">{coins.map(renderRow)}</div>
      </div>
      <div className="flex items-center justify-between border-t pt-3">
        <span className="text-sm font-semibold">{t('totalCountedLabel')}</span>
        <span data-testid="denomination-total" className="text-lg font-bold text-primary">
          {formatCurrency(total)}
        </span>
      </div>
    </div>
  )
}
