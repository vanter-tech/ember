import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { modifierGroupService } from '@/lib/api'
import { Input } from '@/components/ui/input'
import { ArrowUp, ArrowDown, Check } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'
import { cn } from '@/lib/utils'

export type ModifierGroupAssignment = { groupId: number; displayOrder: number }

interface Props {
  value: ModifierGroupAssignment[]
  onChange: (value: ModifierGroupAssignment[]) => void
}

const SEARCH_THRESHOLD = 8

// Literal class names so Tailwind picks them up; each group keeps one color (by id) everywhere.
const GROUP_COLORS = [
  { idle: 'border-rose-300 bg-rose-100 text-rose-800 hover:bg-rose-200', selected: 'border-rose-600 bg-rose-600 text-white', pill: 'border-rose-300 bg-rose-100 text-rose-800 hover:bg-rose-200' },
  { idle: 'border-orange-300 bg-orange-100 text-orange-800 hover:bg-orange-200', selected: 'border-orange-600 bg-orange-600 text-white', pill: 'border-orange-300 bg-orange-100 text-orange-800 hover:bg-orange-200' },
  { idle: 'border-amber-300 bg-amber-100 text-amber-800 hover:bg-amber-200', selected: 'border-amber-600 bg-amber-600 text-white', pill: 'border-amber-300 bg-amber-100 text-amber-800 hover:bg-amber-200' },
  { idle: 'border-lime-300 bg-lime-100 text-lime-800 hover:bg-lime-200', selected: 'border-lime-600 bg-lime-600 text-white', pill: 'border-lime-300 bg-lime-100 text-lime-800 hover:bg-lime-200' },
  { idle: 'border-emerald-300 bg-emerald-100 text-emerald-800 hover:bg-emerald-200', selected: 'border-emerald-600 bg-emerald-600 text-white', pill: 'border-emerald-300 bg-emerald-100 text-emerald-800 hover:bg-emerald-200' },
  { idle: 'border-sky-300 bg-sky-100 text-sky-800 hover:bg-sky-200', selected: 'border-sky-600 bg-sky-600 text-white', pill: 'border-sky-300 bg-sky-100 text-sky-800 hover:bg-sky-200' },
  { idle: 'border-indigo-300 bg-indigo-100 text-indigo-800 hover:bg-indigo-200', selected: 'border-indigo-600 bg-indigo-600 text-white', pill: 'border-indigo-300 bg-indigo-100 text-indigo-800 hover:bg-indigo-200' },
  { idle: 'border-fuchsia-300 bg-fuchsia-100 text-fuchsia-800 hover:bg-fuchsia-200', selected: 'border-fuchsia-600 bg-fuchsia-600 text-white', pill: 'border-fuchsia-300 bg-fuchsia-100 text-fuchsia-800 hover:bg-fuchsia-200' },
]

const colorForGroup = (id?: number) => GROUP_COLORS[Math.abs(id ?? 0) % GROUP_COLORS.length]

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
