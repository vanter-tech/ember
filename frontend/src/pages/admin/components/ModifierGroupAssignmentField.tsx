import { useQuery } from '@tanstack/react-query'
import { modifierGroupService } from '@/lib/api'
import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import { ArrowUp, ArrowDown } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'

export type ModifierGroupAssignment = { groupId: number; displayOrder: number }

interface Props {
  value: ModifierGroupAssignment[]
  onChange: (value: ModifierGroupAssignment[]) => void
}

export const ModifierGroupAssignmentField = ({ value, onChange }: Props) => {
  const { t } = useTranslation('admin')
  const { data: groups = [] } = useQuery({
    queryKey: ['modifierGroups'],
    queryFn: modifierGroupService.getAll,
  })
  const activeGroups = groups.filter((g) => g.active)

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

  return (
    <div className="space-y-3 sm:col-span-2">
      <p className="text-sm font-medium">{t('assignModifierGroupsLabel')}</p>
      {activeGroups.length === 0 && (
        <p className="text-sm text-zinc-500">{t('noModifierGroupsAvailable')}</p>
      )}
      <div className="flex flex-col gap-2">
        {activeGroups.map((group) => (
          <label key={group.id} className="flex items-center gap-2">
            <Checkbox
              checked={value.some((a) => a.groupId === group.id)}
              onCheckedChange={() => group.id && toggle(group.id)}
            />
            {group.name}
          </label>
        ))}
      </div>
      {value.length > 0 && (
        <ul className="flex flex-col gap-1">
          {value
            .slice()
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((assignment, index) => {
              const group = activeGroups.find((g) => g.id === assignment.groupId)
              return (
                <li key={assignment.groupId} className="flex items-center justify-between text-sm">
                  <span>{group?.name}</span>
                  <div className="flex gap-1">
                    <Button type="button" size="icon" variant="ghost" onClick={() => move(index, -1)}>
                      <ArrowUp className="h-4 w-4" />
                    </Button>
                    <Button type="button" size="icon" variant="ghost" onClick={() => move(index, 1)}>
                      <ArrowDown className="h-4 w-4" />
                    </Button>
                  </div>
                </li>
              )
            })}
        </ul>
      )}
    </div>
  )
}
