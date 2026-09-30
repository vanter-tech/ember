import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
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
import { Textarea } from '@/components/ui/textarea'
import { useUIStore } from '@/store/uiStore'
import { billingService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

/**
 * ADMIN-only: closes a table that got stuck (open for days with consumption nobody can charge or
 * close). The backend voids its open bill and refuses a table that already has confirmed payments
 * (409), which is reported separately from a generic failure.
 */
export const ForceCloseTableModal = () => {
  const { t } = useTranslation('waiter')
  const navigate = useNavigate()
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')

  const isOpen = activeModal === 'FORCE_CLOSE_TABLE'
  const sessionId = modalPayload?.sessionId as string | undefined
  const tableNumber = modalPayload?.tableNumber as number | undefined

  const handleClose = () => {
    setReason('')
    closeModal()
  }

  const mutation = useMutation({
    mutationFn: () => billingService.forceCloseTable(sessionId!, reason.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
      queryClient.invalidateQueries({ queryKey: ['sessionDetails'] })
      toast.success(t('forceCloseTableDoneToast'))
      handleClose()
      navigate('/waiter/tables')
    },
    onError: (error) => {
      const hasPayments = axios.isAxiosError(error) && error.response?.status === 409
      toast.error(hasPayments ? t('forceCloseTablePaidError') : t('forceCloseTableErrorToast'))
    },
  })

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('forceCloseTableTitle')}</DialogTitle>
          <DialogDescription className="mt-1 text-sm text-zinc-500">
            {t('forceCloseTableDescription', { table: tableNumber ?? '?' })}
          </DialogDescription>
        </DialogHeader>
        <Textarea
          placeholder={t('forceCloseTableReasonPlaceholder')}
          maxLength={255}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={handleClose} disabled={mutation.isPending}>
            {t('cancelButton')}
          </Button>
          <Button
            variant="destructive"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !sessionId || reason.trim().length < 3}
          >
            {t('forceCloseTableConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
