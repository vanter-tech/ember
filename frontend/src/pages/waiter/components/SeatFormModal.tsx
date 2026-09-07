import { useId, useState } from 'react'
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
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import axios from 'axios'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'

type SeatMode = 'add' | 'rename'

/**
 * Owns every name-only-seat mutation for the table-detail view (Hub build only): the add/rename
 * form (`SEAT_FORM`) and the remove-confirm (`DELETE_SEAT`). Kept in one component so a single
 * place invalidates `sessionDetails` / `bill` after any seat change.
 */
export const SeatFormModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const inputId = useId()

  const formOpen = activeModal === 'SEAT_FORM'
  const deleteOpen = activeModal === 'DELETE_SEAT'
  const sessionId: string | undefined = modalPayload?.sessionId
  const mode: SeatMode = modalPayload?.mode === 'rename' ? 'rename' : 'add'
  const from: string | undefined = modalPayload?.from
  const removingName: string | undefined = modalPayload?.name

  // Reset the field whenever the form (re)opens for a different seat — the store-driven modal has
  // no mount/unmount boundary to hang a `key` off, so adjust state during render on the open key.
  const [value, setValue] = useState('')
  const openKey = formOpen ? `${mode}:${from ?? ''}` : null
  const [seenKey, setSeenKey] = useState<string | null>(null)
  if (openKey !== seenKey) {
    setSeenKey(openKey)
    setValue(formOpen && mode === 'rename' ? (from ?? '') : '')
  }

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['sessionDetails', sessionId] })
    queryClient.invalidateQueries({ queryKey: ['bill', sessionId] })
  }

  const formMutation = useMutation({
    mutationFn: () => {
      const trimmed = value.trim()
      return mode === 'add'
        ? SessionTableService.addSeat(sessionId!, trimmed || undefined)
        : SessionTableService.renameSeat(sessionId!, from!, trimmed)
    },
    onSuccess: () => {
      refresh()
      toast.success(t(mode === 'add' ? 'seatAddedToast' : 'seatRenamedToast'))
      closeModal()
    },
    onError: (e) => {
      if (axios.isAxiosError(e) && e.response?.status === 409 && mode === 'rename') {
        toast.error(t('renameBlockedBillExistsToast'))
        return
      }
      toast.error(t('seatErrorToast'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => SessionTableService.removeSeat(sessionId!, removingName!),
    onSuccess: () => {
      refresh()
      toast.success(t('seatRemovedToast'))
      closeModal()
    },
    onError: () => toast.error(t('seatErrorToast')),
  })

  return (
    <>
      <Dialog open={formOpen} onOpenChange={(open) => !open && closeModal()}>
        <DialogContent className="sm:max-w-md rounded-3xl p-6">
          <DialogHeader className="mb-2">
            <DialogTitle className="text-2xl font-bold text-zinc-800">
              {t(mode === 'add' ? 'addSeatLabel' : 'renameSeatTitle')}
            </DialogTitle>
          </DialogHeader>

          <label htmlFor={inputId} className="text-zinc-500 text-sm">
            {t('seatNameInputLabel')}
          </label>
          <input
            id={inputId}
            value={value}
            maxLength={50}
            autoFocus
            placeholder={t('seatNamePlaceholder', { n: 1 })}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !formMutation.isPending) formMutation.mutate()
            }}
            className="w-full rounded-2xl border-2 border-zinc-200 px-4 py-2 outline-none focus:border-[#8c1717]"
          />

          <DialogFooter>
            <Button
              className="w-full"
              onClick={() => formMutation.mutate()}
              disabled={formMutation.isPending || (mode === 'rename' && !value.trim())}
            >
              {mode === 'add' ? t('addSeatLabel') : t('seatSaveButton')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteOpen} onOpenChange={(open) => !open && closeModal()}>
        <DialogContent className="sm:max-w-md rounded-3xl p-6">
          <DialogHeader className="mb-2">
            <DialogTitle className="text-2xl font-bold text-zinc-800">
              {t('removeSeatTitle')}
            </DialogTitle>
            <DialogDescription className="text-zinc-500 text-sm mt-1">
              {t('removeSeatConfirm', { name: removingName ?? '' })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="destructive"
              className="w-full"
              onClick={() => deleteMutation.mutate()}
              disabled={deleteMutation.isPending}
            >
              {t('seatRemoveConfirmButton')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
