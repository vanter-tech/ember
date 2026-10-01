import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useTranslation } from '@/lib/i18n'

interface BulkDeleteItemsModalProps {
  open: boolean
  count: number
  pending: boolean
  onCancel: () => void
  onConfirm: () => void
}

// One confirmation for the whole selection ("seleccionar todos" then delete), instead of one
// confirmation per dish.
export const BulkDeleteItemsModal = ({ open, count, pending, onCancel, onConfirm }: BulkDeleteItemsModalProps) => {
  const { t } = useTranslation('waiter')

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onCancel()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('bulkDeleteTitle', { count })}
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {t('bulkDeleteWarning')}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="flex-col gap-3 sm:flex-col">
          <Button variant="outline" className="w-full" onClick={onCancel} disabled={pending}>
            {t('bulkDeleteCancel')}
          </Button>
          <Button variant="destructive" className="w-full" onClick={onConfirm} disabled={pending}>
            {pending ? t('bulkDeletePending') : t('bulkDeleteConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
