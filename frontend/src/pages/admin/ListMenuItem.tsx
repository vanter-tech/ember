import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { menuItemService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Card, CardDescription, CardFooter, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Pencil, Trash2, UtensilsCrossed } from 'lucide-react'
import { EmptyState } from '@/components/EmptyState'
import { NewMenuModal } from '@/pages/admin/components/NewMenuModal'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { EditMenuModal } from './components/EditMenuModal'
import { GlobalDeleteModal } from '@/components/GlobalDeleteModal'
import { PaginationControls } from '@/components/PaginationControls'
import { useTranslation } from '@/lib/i18n'
import { cn } from '@/lib/utils'
import { colorForGroup } from '@/lib/modifierGroupColors'

export const ListMenuItem = () => {
  const queryClient = useQueryClient()
  const { t } = useTranslation('admin')
  const toggleActiveOrNotMutation = useMutation({
    mutationFn: menuItemService.toggleAvailability,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] })
      toast.success(t('menuItemUpdatedToast'))
    },
  })

  const { id } = useParams()
  const { openModal } = useUIStore()
  const [page, setPage] = useState(0)
  const {
    data: menuItemsPage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['menuItems', id, page],
    queryFn: () => menuItemService.getAll(Number(id), page),
  })
  const menuItems = menuItemsPage?.content ?? []

  const selectionTypeLabel = (type?: string) => {
    if (type === 'SINGLE_REQUIRED') return t('selectionTypeSingleRequired')
    if (type === 'MULTI_LIMITED') return t('selectionTypeMultiLimited')
    return t('selectionTypeMultiOptional')
  }

  if (isLoading) {
    return <div className="p-6 text-zinc-500">{t('loadingMenuItems')}</div>
  }

  if (isError) {
    return (
      <div className="p-6 text-red-500">{t('loadingMenuItemsError')}</div>
    )
  }

  return (
    <div className="p-6">
      <div
        className={
          menuItems.length === 0
            ? ''
            : 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6'
        }
      >
        {menuItems.length === 0 && (
          <EmptyState
            icon={UtensilsCrossed}
            title={t('menuItemsEmptyTitle')}
            description={t('menuItemsEmptyDescription')}
          />
        )}
        {menuItems.map((menuItem) => (
          <Card
            key={menuItem.id}
            className="flex flex-col gap-0 py-0 shadow-sm overflow-hidden border border-zinc-100 rounded-2xl"
          >
            <div className="relative h-48 bg-zinc-200">
              <img
                src={menuItem.imageUrl || 'https://via.placeholder.com/400'}
                alt={menuItem.name}
                className="w-full h-full object-cover"
              />
              <Badge
                className={cn(
                  'absolute top-4 left-4 bg-white/90 px-3 py-1 text-xs font-semibold rounded-full',
                  menuItem.available ? 'text-green-700' : 'text-zinc-500',
                )}
              >
                {menuItem.available ? t('activeStatus') : t('disabledStatus')}
              </Badge>
            </div>
            <div className="flex-1 flex flex-col gap-2 p-5 pb-3">
              <div className="flex justify-between items-center">
                <CardTitle className="text-xl font-bold text-zinc-800">
                  {menuItem.name}
                </CardTitle>
                <div className="flex gap-2 text-zinc-400">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full bg-zinc-100 text-zinc-600 hover:bg-zinc-200 transition-colors"
                    onClick={(e) => {
                      e.preventDefault()
                      e.stopPropagation()
                      openModal('EDIT_ITEMS', menuItem)
                    }}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full bg-[#8c1717]/10 text-[#8c1717] hover:bg-[#8c1717]/20 transition-colors"
                    onClick={(e) => {
                      e.preventDefault()
                      e.stopPropagation()
                      openModal('DELETE_ITEMS', menuItem)
                    }}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
              <CardDescription>{menuItem.description}</CardDescription>
              {(menuItem.modifierGroups ?? []).length > 0 && (
                <div className="flex flex-wrap gap-1.5" data-testid="menu-item-modifier-groups">
                  {(menuItem.modifierGroups ?? []).map((group) => (
                    <span
                      key={group.id}
                      title={selectionTypeLabel(group.selectionType)}
                      className="inline-flex items-center gap-1.5 rounded-full border border-zinc-200 bg-zinc-50 px-2.5 py-0.5 text-xs font-medium text-zinc-700"
                    >
                      <span
                        className={cn('h-2 w-2 rounded-full', colorForGroup(group.id).dot)}
                        aria-hidden="true"
                      />
                      {group.name}
                    </span>
                  ))}
                </div>
              )}
            </div>
            <CardFooter className="justify-between border-zinc-100 bg-transparent p-4">
              <CardTitle className="text-[#8c1717] text-4xl">
                ${menuItem.price}
              </CardTitle>
              <Switch
                checked={menuItem.available}
                onCheckedChange={() =>
                  toggleActiveOrNotMutation.mutate(Number(menuItem.id))
                }
                className="h-8 w-14 rounded-xl focus-visible:ring[#8c1717] [&>span]:h-7 [&>span]:w-7 data-[state=checked]:[&>span]:translate-x-6"
              />
            </CardFooter>
          </Card>
        ))}
      </div>

      <PaginationControls
        page={page}
        totalPages={menuItemsPage?.totalPages ?? 0}
        onPageChange={setPage}
      />

      <NewMenuModal />
      <EditMenuModal/>
      <GlobalDeleteModal/>
    </div>
  )
}
