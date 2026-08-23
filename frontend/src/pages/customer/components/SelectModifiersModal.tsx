import { useMemo, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import { useTranslation } from '@/lib/i18n'
import type { MenuItemResponse } from '@/lib/api'

interface Props {
  item: MenuItemResponse
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: (selectedOptionIds: number[]) => void
  isPending: boolean
}

export const SelectModifiersModal = ({ item, open, onOpenChange, onConfirm, isPending }: Props) => {
  const { t } = useTranslation('customer')
  const [selected, setSelected] = useState<Record<number, number[]>>({})

  const groups = item.modifierGroups ?? []

  const totalPrice = useMemo(() => {
    const deltas = Object.values(selected)
      .flat()
      .map((optionId) => groups.flatMap((g) => g.options ?? []).find((o) => o.id === optionId)?.priceDelta ?? 0)
    return (item.price ?? 0) + deltas.reduce((a, b) => a + b, 0)
  }, [selected, groups, item.price])

  const canSubmit = groups.every((g) => {
    const count = selected[g.id!]?.length ?? 0
    return count >= (g.minSelections ?? 0)
  })

  const toggleSingle = (groupId: number, optionId: number) => {
    setSelected((prev) => ({ ...prev, [groupId]: [optionId] }))
  }

  const toggleMulti = (groupId: number, optionId: number, max: number | null | undefined) => {
    setSelected((prev) => {
      const current = prev[groupId] ?? []
      if (current.includes(optionId)) {
        return { ...prev, [groupId]: current.filter((id) => id !== optionId) }
      }
      if (max != null && current.length >= max) {
        return prev
      }
      return { ...prev, [groupId]: [...current, optionId] }
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-[#8c1717]">
            {t('selectModifiersDialogTitle')}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6">
          {groups.map((group) => (
            <div key={group.id} className="space-y-2">
              <p className="font-semibold">{group.name}</p>
              <p className="text-xs text-zinc-500">
                {group.selectionType === 'MULTI_LIMITED'
                  ? t('limitedSelectionHint', { min: group.minSelections ?? 0, max: group.maxSelections ?? 0 })
                  : group.selectionType === 'SINGLE_REQUIRED'
                    ? t('requiredSelectionHint')
                    : null}
              </p>
              {group.selectionType === 'SINGLE_REQUIRED' ? (
                <RadioGroup
                  value={String(selected[group.id!]?.[0] ?? '')}
                  onValueChange={(v) => toggleSingle(group.id!, Number(v))}
                >
                  {(group.options ?? []).map((option) => (
                    <label key={option.id} className="flex items-center gap-2">
                      <RadioGroupItem value={String(option.id)} />
                      {option.name} {option.priceDelta ? `(+$${option.priceDelta.toFixed(2)})` : ''}
                    </label>
                  ))}
                </RadioGroup>
              ) : (
                (group.options ?? []).map((option) => (
                  <label key={option.id} className="flex items-center gap-2">
                    <Checkbox
                      checked={selected[group.id!]?.includes(option.id!) ?? false}
                      onCheckedChange={() => toggleMulti(group.id!, option.id!, group.maxSelections)}
                    />
                    {option.name} {option.priceDelta ? `(+$${option.priceDelta.toFixed(2)})` : ''}
                  </label>
                ))
              )}
            </div>
          ))}
        </div>

        <DialogFooter className="mt-4">
          <Button
            className="w-full bg-[#8c1717] hover:bg-[#8c1717]/90"
            disabled={!canSubmit || isPending}
            onClick={() => onConfirm(Object.values(selected).flat())}
          >
            {t('addToCartButton', { price: totalPrice.toFixed(2) })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
