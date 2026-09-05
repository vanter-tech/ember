import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { categoryService, SessionTableService, staffService, type StaffMemberResponse } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Button } from '@/components/ui/button'
import { useTranslation } from '@/lib/i18n'

export const GlobalDeleteModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const { t } = useTranslation('admin')

  const isDeleteModal =
    activeModal === 'DELETE_CATEGORY' ||
    activeModal === 'DELETE_ITEMS' ||
    activeModal === 'DELETE_STAFF'

  const mutation = useMutation({
    mutationFn: async () => {
      if (activeModal === 'DELETE_CATEGORY') {
        return categoryService.delete(modalPayload as number)
      }
      if (activeModal === 'DELETE_ITEMS') {
        return SessionTableService.deleteItem(modalPayload.sessionId, modalPayload.itemId)
      }
      if (activeModal === 'DELETE_STAFF') {
        const member = modalPayload as StaffMemberResponse
        return staffService.updateProfile(member.id!, { active: false })
      }
    },
    onSuccess: () => {
      if (activeModal === 'DELETE_CATEGORY') {
        queryClient.invalidateQueries({ queryKey: ['categories'] })
        toast.success(t('categoryDeletedToast'))
      }
      if (activeModal === 'DELETE_ITEMS') {
        queryClient.invalidateQueries({ queryKey: ['sessionDetails'] })
        toast.success(t('menuItemDeletedToast'))
      }
      if (activeModal === 'DELETE_STAFF') {
        queryClient.invalidateQueries({ queryKey: ['staff'] })
        toast.success(t('staffDeactivatedToast'))
      }
      closeModal()
    },
    onError: () => {
      toast.error(t('deleteErrorToast'))
    }
  })

  const handleDelete = () => {
    mutation.mutate()
  }

  const confirmLabel = activeModal === 'DELETE_STAFF' ? t('confirmDeactivateLabel') : t('confirmDeleteLabel')
  const pendingLabel = activeModal === 'DELETE_STAFF' ? t('deactivatingLabel') : t('deletingLabel')

  return (
    <Dialog
       open={isDeleteModal}
       onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="m-auto text-2xl font-bold text-zinc-800">
            {t('confirmDeleteQuestion')}
          </DialogTitle>
          <DialogDescription className="text-center">
            {activeModal === 'DELETE_STAFF' ? t('deactivateStaffWarning') : t('confirmDeleteWarning')}
          </DialogDescription>
          <div className="flex gap-4 mt-6 flex-col">
            <Button onClick={closeModal} variant="outline" className="w-full">
              {t('cancelButton')}
            </Button>
            <Button
              onClick={handleDelete}
              disabled={mutation.isPending}
              className="w-full"
            >
              {mutation.isPending ? pendingLabel : confirmLabel}
            </Button>
          </div>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}