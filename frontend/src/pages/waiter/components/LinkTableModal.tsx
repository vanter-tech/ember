import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { DashboardService, SessionTableService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'

export const LinkTableModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const { restaurantId } = useAuthStore()
  const queryClient = useQueryClient()

  const isOpen = activeModal === 'LINK_TABLE'
  const sessionId: string | undefined = modalPayload?.sessionId
  const tableNumber: number | undefined = modalPayload?.tableNumber
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data: tables = [] } = useQuery({
    queryKey: ['dashboardData', restaurantId],
    queryFn: () => DashboardService.getDashboardData(),
    enabled: isOpen && !!restaurantId,
  })
  const freeTables = tables.filter((table) => !table.isOccupied)

  const handleClose = () => {
    setSelectedId(null)
    closeModal()
  }

  const mutation = useMutation({
    mutationFn: () => SessionTableService.linkTable(sessionId!, selectedId!),
    onSuccess: () => {
      const linked = freeTables.find((table) => table.tableId === selectedId)
      toast.success(t('linkSuccessToast', { table: linked?.tableNumber ?? '' }))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
      handleClose()
    },
    onError: () => {
      toast.error(t('linkErrorToast'))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
  })

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('linkModalTitle', { table: tableNumber ?? '' })}
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {t('linkModalDescription')}
          </DialogDescription>
        </DialogHeader>

        {freeTables.length === 0 ? (
          <p className="text-sm text-zinc-500 py-4 text-center">{t('linkNoFreeTables')}</p>
        ) : (
          <div className="grid grid-cols-3 gap-2 max-h-72 overflow-y-auto pr-1">
            {freeTables.map((table) => (
              <button
                key={table.tableId}
                type="button"
                onClick={() => setSelectedId(table.tableId!)}
                className={`rounded-2xl border-2 px-3 py-4 font-semibold transition-colors ${
                  selectedId === table.tableId
                    ? 'border-[#8B0000] bg-[#8B0000]/5'
                    : 'border-zinc-200 hover:border-zinc-300'
                }`}
              >
                {t('linkTableOption', { table: table.tableNumber ?? '' })}
              </button>
            ))}
          </div>
        )}

        <DialogFooter>
          <Button
            className="w-full"
            onClick={() => mutation.mutate()}
            disabled={!selectedId || mutation.isPending}
          >
            {t('linkSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
