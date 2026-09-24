import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { modifierGroupService } from '@/lib/api'
import { Input } from '@/components/ui/input'
import { ArrowUp, ArrowDown, Check } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'
import { cn } from '@/lib/utils'
import { colorForGroup } from '@/lib/modifierGroupColors'

export type ModifierGroupAssignment = { groupId: number; displayOrder: number }

interface Props {
  value: ModifierGroupAssignment[]
  onChange: (value: ModifierGroupAssignment[]) => void
}

const SEARCH_THRESHOLD = 8

export const ModifierGroupAssignmentField = ({ value, onChange }: Props) => {
  const { t } = useTranslation('admin')
  const [query, setQuery] = useState('')
  const { data: groups = [] } = useQuery({
    queryKey: ['modifierGroups'],
    queryFn: modifierGroupService.getAll,
  })
  const activeGroups = groups.filter((g) => g.active)
  const showSearch = activeGroups.length > SEARCH_THRESHOLD
  const normalizedQuery = query.trim().toLowerCase()
  const visibleGroups = normalizedQuery
    ? activeGroups.filter((g) => (g.name ?? '').toLowerCase().includes(normalizedQuery))
    : activeGroups

  const selectionTypeLabel = (type?: string) => {
    if (type === 'SINGLE_REQUIRED') return t('selectionTypeSingleRequired')
    if (type === 'MULTI_LIMITED') return t('selectionTypeMultiLimited')
    return t('selectionTypeMultiOptional')
  }

  const toggle = (groupId: number) => {
    const exists = value.some((a) => a.groupId === groupId)
    if (exists) {
      onChange(value.filter((a) => a.groupId !== groupId)
        .map((a, i) => ({ ...a, displayOrder: i })))
    } else {
      onChange([...value, { groupId, displayOrder: value.length }])
    }
  }

  const move = (index: number, direction: -1 | 1) => {
    const next = [...value]
    const swap = index + direction
    if (swap < 0 || swap >= next.length) return
    ;[next[index], next[swap]] = [next[swap], next[index]]
    onChange(next.map((a, i) => ({ ...a, displayOrder: i })))
  }

  const ordered = value.slice().sort((a, b) => a.displayOrder - b.displayOrder)

  return (
    <div className="space-y-3 sm:col-span-2">
      <p className="text-sm font-medium">{t('assignModifierGroupsLabel')}</p>
      {activeGroups.length === 0 && (
        <p className="text-sm text-zinc-500">{t('noModifierGroupsAvailable')}</p>
      )}
      {showSearch && (
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('searchModifierGroupsPlaceholder')}
          aria-label={t('searchModifierGroupsPlaceholder')}
        />
      )}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {visibleGroups.map((group) => {
          const selected = value.some((a) => a.groupId === group.id)
          const color = colorForGroup(group.id)
          return (
            <button
              key={group.id}
              type="button"
              aria-pressed={selected}
              onClick={() => group.id && toggle(group.id)}
              className={cn(
                'flex cursor-pointer flex-col items-start gap-0.5 rounded-2xl border px-3 py-2 text-left text-sm transition-colors',
                selected ? color.selected : color.idle
              )}
            >
              <span className="flex w-full items-center justify-between gap-2 font-medium">
                <span className="truncate">{group.name}</span>
                {selected && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
              </span>
              <span className={cn('text-xs', selected ? 'text-white/80' : 'opacity-75')}>
                {selectionTypeLabel(group.selectionType)} ·{' '}
                {t('modifierGroupOptionsCount', { count: group.options?.length ?? 0 })}
              </span>
            </button>
          )
        })}
      </div>
      {ordered.length > 0 && (
        <ol className="flex flex-wrap gap-2">
          {ordered.map((assignment, index) => {
            const group = activeGroups.find((g) => g.id === assignment.groupId)
            const name = group?.name ?? ''
            const color = colorForGroup(assignment.groupId)
            return (
              <li
                key={assignment.groupId}
                className={cn('flex items-center gap-1 rounded-full border py-1 pl-3 pr-1 text-sm', color.pill)}
              >
                <span className="font-semibold">{index + 1}.</span>
                <span>{name}</span>
                <button
                  type="button"
                  aria-label={t('moveModifierGroupUpAria', { name })}
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                  className="cursor-pointer rounded-full p-1 hover:bg-black/10 disabled:cursor-not-allowed disabled:opacity-30"
                >
                  <ArrowUp className="h-3.5 w-3.5" />
                </button>
                <button
                  type="button"
                  aria-label={t('moveModifierGroupDownAria', { name })}
                  disabled={index === ordered.length - 1}
                  onClick={() => move(index, 1)}
                  className="cursor-pointer rounded-full p-1 hover:bg-black/10 disabled:cursor-not-allowed disabled:opacity-30"
                >
                  <ArrowDown className="h-3.5 w-3.5" />
                </button>
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}
