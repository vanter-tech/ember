import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { cashDrawerService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'
import { CASH_DRAWER_QUERY_KEY } from './useCashDrawerEvents'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export const ManualOpenDialog = ({ open, onOpenChange }: Props) => {
  const { t } = useTranslation('waiter')
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')

  const mutation = useMutation({
    mutationFn: () => cashDrawerService.open(reason.trim()),
    onSuccess: (event) => {
      queryClient.invalidateQueries({ queryKey: CASH_DRAWER_QUERY_KEY })
      if (event.drawer === 'FAILED') toast.error(t('drawerFailedToast'))
      else toast.success(t('drawerOpenedToast'))
      setReason('')
      onOpenChange(false)
    },
    onError: () => toast.error(t('drawerFailedToast')),
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader>
          <DialogTitle>{t('drawerManualTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="drawer-reason">{t('drawerManualReasonLabel')}</Label>
          <Textarea
            id="drawer-reason"
            value={reason}
            maxLength={255}
            placeholder={t('drawerManualReasonPlaceholder')}
            onChange={(e) => setReason(e.target.value)}
          />
          <p className="text-xs text-zinc-400">{t('drawerManualShortcutHint')}</p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={mutation.isPending}>
            {t('drawerManualCancel')}
          </Button>
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || reason.trim().length < 3}>
            {t('drawerManualConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Admin-facing trigger (the accountant uses F9 via CashDrawerWatcher). */
export const ManualOpenButton = () => {
  const { t } = useTranslation('waiter')
  const [open, setOpen] = useState(false)
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        {t('drawerManualButton')}
      </Button>
      <ManualOpenDialog open={open} onOpenChange={setOpen} />
    </>
  )
}
