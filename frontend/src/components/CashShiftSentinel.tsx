import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { cashShiftService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'
import { deriveCashShiftAlert, REMINDER_INTERVAL_MS } from '@/lib/cashShiftAlert'
import { CloseShiftDialog } from '@/pages/waiter/cashRegister/components/CloseShiftDialog'

export const CashShiftSentinel = () => {
  const { t } = useTranslation('waiter')
  const { openModal, setCashShiftAlertOpen } = useUIStore()
  const queryClient = useQueryClient()
  const [now, setNow] = useState(() => new Date())
  // A timestamp; PRE_WARNING / OVERDUE modals are suppressed while now < dismissedUntil.
  // Kept in state (not a ref) so the modal reappears on the next 30 s tick after it lapses.
  const [dismissedUntil, setDismissedUntil] = useState(0)

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 30_000)
    return () => clearInterval(id)
  }, [])

  const { data: shift } = useQuery({
    queryKey: ['cashShiftCurrent'],
    queryFn: cashShiftService.current,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })

  const alert = deriveCashShiftAlert(shift ?? null, now)

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', shift?.id],
    queryFn: () => cashShiftService.detail(shift!.id!),
    enabled: alert === 'STALE' && !!shift?.id,
  })

  const prolong = useMutation({
    mutationFn: () => cashShiftService.prolong(shift!.id!),
    onSuccess: () => {
      setDismissedUntil(0)
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      toast.success(t('cashShiftProlongedToast'))
    },
    onError: () => toast.error(t('cashShiftProlongErrorToast')),
  })

  const snooze = () => setDismissedUntil(Date.now() + REMINDER_INTERVAL_MS)
  const closeShift = () => { if (shift) openModal('CLOSE_SHIFT', { shiftId: shift.id }) }

  const suppressed = now.getTime() < dismissedUntil
  const showPreWarning = alert === 'PRE_WARNING' && !suppressed
  const showOverdue = alert === 'OVERDUE' && !suppressed
  // STALE is dismissible too: the waiter often has to leave this screen to close
  // the still-open tables before the shift can be closed. Snoozing lets them do
  // that; the modal returns on the next 30 s tick once the snooze lapses.
  const showStale = alert === 'STALE' && !suppressed
  const showAnyAlert = showPreWarning || showOverdue || showStale

  // QA_SIMULATION_REPORT.md E-17: let WaiterTour (and any future consumer) know a blocking
  // alert is up, so it can hold off starting rather than stacking on top of this one.
  useEffect(() => {
    setCashShiftAlertOpen(showAnyAlert)
  }, [showAnyAlert, setCashShiftAlertOpen])
  useEffect(() => () => setCashShiftAlertOpen(false), [setCashShiftAlertOpen])

  if (!shift) return null

  const deadlineLabel = shift.effectiveDeadline
    ? new Date(shift.effectiveDeadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : ''

  return (
    <>
      {/* Mounted here (not just on the cash-register page) so the "Cerrar caja"
          action in the alerts below works from any screen the sentinel shows on. */}
      <CloseShiftDialog />

      <AlertDialog open={showPreWarning} onOpenChange={(o) => !o && snooze()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('cashShiftPreWarningTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('cashShiftPreWarningBody', { time: deadlineLabel })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={snooze}>{t('cashShiftLaterButton')}</AlertDialogCancel>
            <AlertDialogAction onClick={() => prolong.mutate()} disabled={prolong.isPending}>
              {t('cashShiftProlongButton')}
            </AlertDialogAction>
            <AlertDialogAction onClick={closeShift}>{t('cashShiftCloseButton')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={showOverdue} onOpenChange={(o) => !o && snooze()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('cashShiftOverdueTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('cashShiftOverdueBody', { time: deadlineLabel })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={snooze}>{t('cashShiftLaterButton')}</AlertDialogCancel>
            <AlertDialogAction onClick={() => prolong.mutate()} disabled={prolong.isPending}>
              {t('cashShiftProlongButton')}
            </AlertDialogAction>
            <AlertDialogAction onClick={closeShift}>{t('cashShiftCloseButton')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={showStale} onOpenChange={(o) => !o && snooze()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t('cashShiftStaleTitle', { date: shift.businessDay ?? '' })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t('cashShiftStaleBody', { date: shift.businessDay ?? '' })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="max-h-64 overflow-y-auto text-sm">
            <p className="font-medium">
              {t('cashShiftStaleMovementsCount', { count: detail?.movements?.length ?? 0 })}
            </p>
            <p className="font-medium">
              {t('cashShiftStalePaymentsCount', { count: detail?.payments?.length ?? 0 })}
            </p>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={snooze}>{t('cashShiftLaterButton')}</AlertDialogCancel>
            <AlertDialogAction onClick={closeShift}>
              {t('cashShiftStaleCloseButton', { date: shift.businessDay ?? '' })}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
