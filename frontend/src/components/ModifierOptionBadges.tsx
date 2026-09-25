import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'
import { colorForGroup } from '@/lib/modifierGroupColors'

interface Option {
  id?: number
  name?: string
  priceDelta?: number | null
}

interface Props {
  groupId?: number
  options: Option[]
  selectedIds: number[]
  single: boolean
  onToggle: (optionId: number) => void
}

export const ModifierOptionBadges = ({ groupId, options, selectedIds, single, onToggle }: Props) => {
  const color = colorForGroup(groupId)
  return (
    <div role={single ? 'radiogroup' : 'group'} className="flex flex-wrap gap-2">
      {options.map((option) => {
        const selected = selectedIds.includes(option.id!)
        return (
          <button
            key={option.id}
            type="button"
            role={single ? 'radio' : undefined}
            aria-checked={single ? selected : undefined}
            aria-pressed={single ? undefined : selected}
            onClick={() => onToggle(option.id!)}
            className={cn(
              'flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition-colors',
              selected ? cn(color.badgeOn, 'font-semibold') : cn(color.badge, 'hover:brightness-95')
            )}
          >
            <span className={cn('h-2.5 w-2.5 shrink-0 rounded-full', color.dot)} aria-hidden="true" />
            <span>{option.name}</span>
            {option.priceDelta ? (
              <span className="text-xs text-zinc-500">+${option.priceDelta.toFixed(2)}</span>
            ) : null}
            {selected && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
          </button>
        )
      })}
    </div>
  )
}
