import { Link } from 'react-router-dom'
import { useEffect, useRef } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useSessionStore } from '@/store/sessionStore'
import { useAuthStore } from '@/store/authStore'
import { useSettingStore } from '@/store/settingStore'
import { billingService, loyaltyAccountService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ArrowLeft, CreditCard, CheckCircle2, Clock, Sparkles } from 'lucide-react'
import toast from 'react-hot-toast'
import { TIER_LABELS } from '@/pages/admin/components/settings/loyalty/types'
import { useTranslation } from '@/lib/i18n'

export const Bill = () => {
  const { t } = useTranslation('customer')
  const bill = useSessionStore((state) => state.bill)
  const billSplits = useSessionStore((state) => state.billSplits)
  const sessionId = useSessionStore((state) => state.id)
  const participants = useSessionStore((state) => state.participants)
  const setBillReady = useSessionStore((state) => state.setBillReady)
  const currentId = useAuthStore((state) => state.userId)

  // Always ask the server on open, not only when the store has no bill: the store is persisted, so
  // after a reload it holds whatever the last live frame left — e.g. a split still UNPAID because
  // the SPLIT_PAID / SESSION_CLOSED frames arrived while this page had no live subscription. That
  // made "Pagar mi parte" reappear for a share the server already has PAID. Live WS updates take
  // over from here (the effect only re-runs when the fetched bill itself changes).
  const clearBill = useSessionStore((state) => state.clearBill)
  // The bill this page opened with (the persisted one). A live BILL_READY frame that lands while the
  // request is in flight replaces it, and a stale "no bill" answer must not wipe that newer bill.
  const openedWithBillId = useRef(bill?.id)
  const { data: fetchedBill } = useQuery({
    queryKey: ['billState', sessionId],
    queryFn: () => billingService.getBillState(sessionId!),
    enabled: !!sessionId,
    retry: false,
  })

  useEffect(() => {
    if (fetchedBill === undefined) return
    if (fetchedBill === null) {
      // the server has no bill (voided/never requested): drop a stale persisted one
      if (useSessionStore.getState().bill?.id === openedWithBillId.current) clearBill()
      return
    }
    setBillReady({ id: fetchedBill.id, total: fetchedBill.total }, fetchedBill.splits ?? [])
  }, [fetchedBill, setBillReady, clearBill])

  const myName = participants?.find((p) => p.userId === currentId)?.name
  const mySplit = billSplits?.find((split) => split.participantName === myName)

  // The bill only ever carries the tax-inclusive `total` (BillReadyMessage / getBillState), so
  // rebuild the breakdown from the tenant's configured rate — the same rate the backend applied
  // in BillingService.calculateBill (`total = subtotal * (1 + rate/100)`). Shown only when a rate
  // is set; with no tax configured the bill just shows its total as before.
  const { settings } = useSettingStore()
  const taxRatePercent = settings?.billing?.taxRate ?? 0
  const billTotal = bill?.total ?? 0
  const subtotal = taxRatePercent > 0 ? billTotal / (1 + taxRatePercent / 100) : billTotal
  const taxAmount = billTotal - subtotal
  const { data: loyaltyAccount } = useQuery({
    queryKey: ['loyaltyAccount', 'me'],
    queryFn: loyaltyAccountService.me,
    enabled: mySplit?.status === 'PAID',
    retry: false,
  })

  const payMutation = useMutation({
    mutationFn: () =>
      billingService.initiateDigitalPayment(bill!.id!, myName!, mySplit!.amount!),
    onSuccess: () => {
      toast.success(t('billPaymentSentToast'))
    },
    onError: () => toast.error(t('billPaymentErrorToast')),
  })

  const paymentRequested = payMutation.isSuccess

  return (
    <div className="p-6 pt-0 bg-slate-50 min-h-screen">
      <header className="flex flex-row items-center gap-3 pb-5 border-b-2">
        <Link to={`/customer/menu/${sessionId}/comanda`}>
          <Button className="w-15 h-15 rounded-full">
            <ArrowLeft className="w-5 h-5" />
          </Button>
        </Link>
        <h2 className="text-2xl text-[#8c1717] font-bold uppercase">
          {t('billTitle')}
        </h2>
      </header>

      <div className="pt-6 max-w-xl mx-auto flex flex-col gap-4">
        {!bill ? (
          <Card>
            <CardContent className="py-12 text-center text-gray-400">
              {t('billNotRequestedYet')}
            </CardContent>
          </Card>
        ) : (
          <>
            <Card>
              <CardHeader>
                <CardTitle className="flex justify-between items-center">
                  <span>{t('billTableTotal')}</span>
                  <span className="text-2xl text-[#8c1717] font-bold">
                    ${bill.total?.toFixed(2)}
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                {taxRatePercent > 0 && (
                  <div className="flex flex-col gap-1 border-b pb-3 text-sm text-gray-500">
                    <div className="flex justify-between">
                      <span>{t('billSubtotalLabel')}</span>
                      <span>${subtotal.toFixed(2)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>{t('billTaxLabel', { rate: taxRatePercent })}</span>
                      <span>${taxAmount.toFixed(2)}</span>
                    </div>
                  </div>
                )}
                {billSplits?.map((split) => (
                  <div
                    key={split.participantName}
                    className={`flex items-center justify-between p-4 rounded-2xl ${
                      split.participantName === myName
                        ? 'bg-[#8c1717]/5 border-2 border-[#8c1717]/20'
                        : 'bg-gray-50'
                    }`}
                  >
                    <div className="flex flex-col">
                      <span className="font-semibold">
                        {split.participantName}
                        {split.participantName === myName && t('billYouSuffix')}
                      </span>
                      <span className="text-sm text-gray-500">
                        ${split.amount?.toFixed(2)}
                      </span>
                    </div>
                    {split.status === 'PAID' || split.status === 'PARTIALLY_PAID' ? (
                      <Badge className="flex items-center gap-1">
                        <CheckCircle2 className="w-4 h-4" /> {t('billStatusPaid')}
                      </Badge>
                    ) : (
                      <Badge variant="outline">{t('billStatusPending')}</Badge>
                    )}
                  </div>
                ))}
              </CardContent>
            </Card>

            {loyaltyAccount && (
              <Card className="bg-[#8c1717]/5 border-2 border-[#8c1717]/20">
                <CardContent className="py-5 flex items-center gap-3">
                  <Sparkles className="w-8 h-8 text-[#8c1717] shrink-0" />
                  <div className="flex flex-col">
                    <span className="font-semibold">
                      {t('billPointsEarned', { points: loyaltyAccount.totalPoints! })}
                    </span>
                    <span className="text-sm text-gray-500">
                      {t('billLoyaltyTierLabel', { tierName: TIER_LABELS[loyaltyAccount.tier!] })}
                      {loyaltyAccount.nextTier &&
                        t('billPointsToNextTier', {
                          points: loyaltyAccount.pointsToNextTier!,
                          tierName: TIER_LABELS[loyaltyAccount.nextTier],
                        })}
                    </span>
                  </div>
                </CardContent>
              </Card>
            )}

            {mySplit && mySplit.status === 'UNPAID' && (
              <Button
                className="w-full h-15 text-xl font-bold gap-2"
                disabled={payMutation.isPending || paymentRequested}
                onClick={() => payMutation.mutate()}
              >
                {paymentRequested ? (
                  <>
                    <Clock className="w-5 h-5" /> {t('billWaitingConfirmation')}
                  </>
                ) : (
                  <>
                    <CreditCard className="w-5 h-5" />{' '}
                    {t('billPayMyShare', { amount: mySplit.amount?.toFixed(2) ?? '0.00' })}
                  </>
                )}
              </Button>
            )}
          </>
        )}
      </div>
    </div>
  )
}
