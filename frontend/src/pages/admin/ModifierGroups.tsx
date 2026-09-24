import { useQuery } from '@tanstack/react-query'
import { modifierGroupService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Card, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Pencil, SlidersHorizontal } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/EmptyState'
import { NewModifierGroupModal } from './components/NewModifierGroupModal'
import { EditModifierGroupModal } from './components/EditModifierGroupModal'
import { SectionTour } from '@/components/tours/SectionTour'
import { useTranslation } from '@/lib/i18n'
import { cn } from '@/lib/utils'
import { colorForGroup } from '@/lib/modifierGroupColors'

// Card, type badge and option chips share the group's color; hover styles are dropped (static).
const tone = (id?: number) => {
  const idle = colorForGroup(id).idle.replace(/\shover:\S+/g, '')
  return { card: idle, badge: idle, chip: idle }
}

export const ModifierGroups = () => {
  const { openModal } = useUIStore()
  const { t } = useTranslation('admin')

  const tourSteps = [
    {
      target: '#modifiers-tour-grid',
      title: t('tourModifiersGridTitle'),
      content: t('tourModifiersGridContent'),
      skipBeacon: true,
    },
    {
      target: '#topnav-create-button',
      title: t('tourModifiersCreateTitle'),
      content: t('tourModifiersCreateContent'),
    },
  ]

  const { data: groups = [], isLoading, isError } = useQuery({
    queryKey: ['modifierGroups'],
    queryFn: modifierGroupService.getAll,
  })

  const selectionTypeLabel = (type?: string) => {
    if (type === 'SINGLE_REQUIRED') return t('selectionTypeSingleRequired')
    if (type === 'MULTI_LIMITED') return t('selectionTypeMultiLimited')
    if (type === 'MULTI_OPTIONAL') return t('selectionTypeMultiOptional')
    return type ?? ''
  }

  if (isLoading) return <div className="p-6 text-zinc-500">{t('loadingModifierGroups')}</div>
  if (isError) return <div className="p-6 text-red-500">{t('loadingModifierGroupsError')}</div>

  return (
    <div>
      <div
        id="modifiers-tour-grid"
        className={groups.length === 0 ? '' : 'grid grid-cols-1 md:grid-cols-2 gap-4'}
      >
        {groups.length === 0 && (
          <EmptyState
            icon={SlidersHorizontal}
            title={t('modifierGroupsEmptyTitle')}
            description={t('modifierGroupsEmptyDescription')}
          />
        )}
        {groups.map((group) => (
          <Card
            key={group.id}
            className={cn('p-4 rounded-3xl flex flex-col gap-2 border', tone(group.id).card)}
          >
            <div className="flex items-center justify-between">
              <CardTitle>{group.name}</CardTitle>
              <Button variant="ghost" size="icon" onClick={() => openModal('EDIT_MODIFIER_GROUP', group)}>
                <Pencil className="h-4 w-4" />
              </Button>
            </div>
            <Badge variant="outline" className={cn('w-fit', tone(group.id).badge)}>
              {selectionTypeLabel(group.selectionType)}
            </Badge>
            <div className="flex flex-wrap gap-1.5">
              {(group.options ?? [])
                .filter((o) => o.active !== false)
                .map((o) => (
                  <span
                    key={o.id}
                    className={cn(
                      'rounded-full border px-2.5 py-0.5 text-xs font-medium',
                      tone(group.id).chip
                    )}
                  >
                    {o.name}
                    {(o.priceDelta ?? 0) > 0 && ` +$${o.priceDelta}`}
                  </span>
                ))}
            </div>
          </Card>
        ))}
      </div>
      <NewModifierGroupModal />
      <EditModifierGroupModal />
      <SectionTour sectionId="admin-inventory-modifiers" steps={tourSteps} />
    </div>
  )
}
