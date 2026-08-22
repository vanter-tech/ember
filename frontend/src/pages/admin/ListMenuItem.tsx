import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { menuItemService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Card, CardDescription, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Pencil, Trash2 } from 'lucide-react'
import { NewMenuModal } from '@/pages/admin/components/NewMenuModal'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { EditMenuModal } from './components/EditMenuModal'
import { GlobalDeleteModal } from '@/components/GlobalDeleteModal'
import { PaginationControls } from '@/components/PaginationControls'
import { useTranslation } from '@/lib/i18n'

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
      <div className="grid grid:cols-1 gap-6">
        {menuItems.map((menuItem) => (
          <Card
            key={menuItem.id}
            className="flex flex-row items-center  shadow-sm overflow-hidden border border-zinc-100 rounded-4xl"
          >
            <div className="relative w-auto h-48 bg-zinc-200">
              <img
                src={menuItem.imageUrl || 'https://via.placeholder.com/400'}
                className="w-full h-full object-cover"
              />
              {menuItem.available ? (
                <Badge
                  className="absolute top-4 left-4 bg-white/90 px-3 py-1 text-xs
                font-semibold text-green-700 rounded-full"
                >
                  {t('activeStatus')}
                </Badge>
              ) : (
                <Badge
                  className="absolute top-4 left-4 bg-white/90 px-3 py-1 text-xs
                font-semibold text-green-700 rounded-full"
                >
                  {t('disabledStatus')}
                </Badge>
              )}
            </div>
            <div className="flex-1 flex flex-col gap-2 m-2">
              <CardTitle className="text-md md:text-xl lg:text-2xl">
                {menuItem.name}
              </CardTitle>
              <CardDescription>{menuItem.description}</CardDescription>
            </div>
            <div className="flex flex-col items-end gap-6 p-4">
              <div className="">
                <Button
                  variant="ghost"
                  size="icon"
                  className="hover-text-zinc-600 transition-colors"
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
                  className="hover-text-[#8c1717] transition-colors text-[#8c1717]"
                  onClick={(e) => {
                    e.preventDefault()
                    e.stopPropagation()
                    openModal('DELETE_ITEMS', menuItem)
                  }}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
              <CardTitle className="text-[#8c1717] text-3xl">
                ${menuItem.price}
              </CardTitle>
              <Switch
                checked={menuItem.available}
                onCheckedChange={() =>
                  toggleActiveOrNotMutation.mutate(Number(menuItem.id))
                }
                className="rounded-xl focus-visible:ring[#8c1717]"
              />
            </div>
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
