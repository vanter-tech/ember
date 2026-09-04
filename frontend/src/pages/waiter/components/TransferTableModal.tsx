import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQuery } from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'

export const TransferTableModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const navigate = useNavigate()

  const isOpen = activeModal === 'TRANSFER_TABLE'
  const sessionId: string | undefined = modalPayload?.sessionId
  const currentWaiterEmail: string | undefined = modalPayload?.currentWaiterEmail

  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data: waiters = [] } = useQuery({
    queryKey: ['waiters'],
    queryFn: SessionTableService.listWaiters,
    enabled: isOpen,
  })

  const options = useMemo(
    () => waiters.filter((w) => w.email !== currentWaiterEmail),
    [waiters, currentWaiterEmail],
  )

  const handleClose = () => {
    setSelectedId(null)
    closeModal()
  }

  const mutation = useMutation({
    mutationFn: () => SessionTableService.transferTable(sessionId!, selectedId!),
    onSuccess: () => {
      const name = options.find((w) => w.id === selectedId)?.name ?? ''
      toast.success(t('transferSuccessToast', { name }))
      handleClose()
      navigate('/waiter/tables')
    },
    onError: () => {
      toast.error(t('transferErrorToast'))
    },
  })

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('transferModalTitle')}
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {t('transferModalDescription')}
          </DialogDescription>
        </DialogHeader>

        {options.length === 0 ? (
          <p className="text-sm text-zinc-500 py-4 text-center">{t('transferNoWaiters')}</p>
        ) : (
          <div className="flex flex-col gap-2 max-h-72 overflow-y-auto pr-1">
            {options.map((waiter) => (
              <button
                key={waiter.id}
                type="button"
                onClick={() => setSelectedId(waiter.id)}
                className={`text-left rounded-2xl border-2 px-4 py-3 transition-colors ${
                  selectedId === waiter.id
                    ? 'border-[#8B0000] bg-[#8B0000]/5'
                    : 'border-zinc-200 hover:border-zinc-300'
                }`}
              >
                <span className="font-semibold block">{waiter.name}</span>
                <span className="text-sm text-zinc-500">{waiter.email}</span>
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
            {t('transferSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
