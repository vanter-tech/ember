import { useQuery } from '@tanstack/react-query'
import { modifierGroupService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Card, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Pencil } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { NewModifierGroupModal } from './components/NewModifierGroupModal'
import { EditModifierGroupModal } from './components/EditModifierGroupModal'
import { SectionTour } from '@/components/tours/SectionTour'
import { useTranslation } from '@/lib/i18n'

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

  if (isLoading) return <div className="p-6 text-zinc-500">{t('loadingModifierGroups')}</div>
  if (isError) return <div className="p-6 text-red-500">{t('loadingModifierGroupsError')}</div>

  return (
    <div>
      <div id="modifiers-tour-grid" className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {groups.map((group) => (
          <Card key={group.id} className="p-4 rounded-3xl flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <CardTitle>{group.name}</CardTitle>
              <Button variant="ghost" size="icon" onClick={() => openModal('EDIT_MODIFIER_GROUP', group)}>
                <Pencil className="h-4 w-4" />
              </Button>
            </div>
            <Badge variant="outline" className="w-fit">{group.selectionType}</Badge>
            <p className="text-sm text-zinc-500">
              {group.options?.map((o) => o.name).join(', ')}
            </p>
          </Card>
        ))}
      </div>
      <NewModifierGroupModal />
      <EditModifierGroupModal />
      <SectionTour sectionId="admin-inventory-modifiers" steps={tourSteps} />
    </div>
  )
}
