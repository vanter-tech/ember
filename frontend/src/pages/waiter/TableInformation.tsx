import { useParams, Link } from 'react-router-dom'
import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SessionTableService, billingService, type WaiterBillState } from '@/lib/api'
import { Button } from '@/components/ui/button'
import {
  ArrowLeft,
  User,
  Printer,
  ArrowRightLeft,
  Plus,
  Trash2,
  Banknote,
  CreditCard,
  CheckCircle2,
  Ban,
  RotateCcw,
  UserMinus,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import toast from 'react-hot-toast'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { useUIStore } from '@/store/uiStore'
import { GlobalDeleteModal } from '../../components/GlobalDeleteModal'
import { ChargeTableModal } from './components/ChargeTableModal'
import { VoidBillModal } from './components/VoidBillModal'
import { RefundPaymentModal } from './components/RefundPaymentModal'
import { useWebsocketStore } from '@/store/websocket'
import { useTranslation } from '@/lib/i18n'
import { SectionTour } from '@/components/tours/SectionTour'
import type { Step } from 'react-joyride'

export const TableInformation = () => {
  const { t } = useTranslation('waiter')
  const { id } = useParams()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { openModal } = useUIStore()
  const {
    isConnected,
    stompClient,
    subscribeToWaiterSession,
    unsubscribeFromWaiterSession,
    lastBillRedistribution,
    clearBillRedistribution,
  } = useWebsocketStore()

  const { data: sessionData, isPending: isLoadingData } = useQuery({
    queryKey: ['sessionDetails', id],
    queryFn: () => SessionTableService.sessionInformation(id!),
  })

  const { data: billData } = useQuery<WaiterBillState | null>({
    queryKey: ['bill', id],
    queryFn: () => billingService.getBillState(id!),
    enabled: !!id,
  })

  useEffect(() => {
    if (id && isConnected && stompClient?.connected) {
      subscribeToWaiterSession(id)
    }
    return () => {
      unsubscribeFromWaiterSession()
    }
  }, [id, isConnected, stompClient, subscribeToWaiterSession, unsubscribeFromWaiterSession])

  useEffect(() => {
    if (sessionData?.status === 'CLOSED') {
      toast.success(t('tableClosedPaidToast'))
      navigate('/waiter/tables')
    }
  }, [sessionData?.status, navigate])

  useEffect(() => {
    if (!lastBillRedistribution) return
    toast(t('splitRedistributedToast', { name: lastBillRedistribution.departedParticipantName }))
    clearBillRedistribution()
  }, [lastBillRedistribution, clearBillRedistribution, t])

  const itemsToWaiter = sessionData?.items
    ? sessionData.items.filter((item) => item.status != 'DRAFT')
    : []

  const mutation = useMutation({
    mutationFn: SessionTableService.closeEmptySession,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessionDetails'] })
      toast.success(t('tableClosedToast'))
      navigate('/waiter/tables')
    },
  })

  const physicalPaymentMutation = useMutation({
    mutationFn: ({
      participantName,
      amount,
    }: {
      participantName: string
      amount: number
    }) => billingService.registerPhysicalPayment(billData!.id, participantName, amount),
    onSuccess: () => toast.success(t('cashPaymentRegisteredToast')),
    onError: (error) => {
      const code =
        axios.isAxiosError(error) && (error.response?.data as { code?: string })?.code
      toast.error(
        code === 'CASH_SHIFT_OVERDUE'
          ? t('cashShiftOverduePaymentToast')
          : t('cashPaymentErrorToast'),
      )
    },
  })

  const confirmDigitalPaymentMutation = useMutation({
    mutationFn: (paymentId: number) => billingService.confirmDigitalPayment(paymentId),
    onSuccess: () => toast.success(t('digitalPaymentConfirmedToast')),
    onError: () => toast.error(t('digitalPaymentErrorToast')),
  })

  const redistributeSplitMutation = useMutation({
    mutationFn: (departingParticipantName: string) =>
      billingService.redistributeSplit(billData!.id, departingParticipantName),
    onSuccess: () => toast.success(t('splitRedistributedDoneToast')),
    onError: () => toast.error(t('splitRedistributeErrorToast')),
  })

  const settleAndCloseMutation = useMutation({
    mutationFn: () => billingService.settleAndClose(billData!.id),
    onSuccess: () => toast.success(t('tableSettledToast')),
    onError: () => toast.error(t('tableSettleErrorToast')),
  })

  if (isLoadingData) {
    return <div className="p-6 text-zinc-500">{t('loadingDashboard')}</div>
  }

  const hasItems = itemsToWaiter && itemsToWaiter.length > 0
  const hasBillableItems = itemsToWaiter.some(
    (item) => item.status === 'READY' || item.status === 'DELIVERED'
  )

  const subtotal =
    itemsToWaiter.reduce((total, item) => total + (item.price ?? 0), 0) ??
    0
  const taxes = subtotal * 0.1
  const total = subtotal + taxes

  const tourSteps: Step[] = [
    { target: '#table-tour-actions', title: t('tourTableActionsTitle'), content: t('tourTableActionsContent'), skipBeacon: true },
    { target: '#table-tour-orders', title: t('tourTableOrdersTitle'), content: t('tourTableOrdersContent') },
    { target: '#table-tour-participants', title: t('tourTableParticipantsTitle'), content: t('tourTableParticipantsContent') },
    { target: '#table-tour-activity', title: t('tourTableActivityTitle'), content: t('tourTableActivityContent') },
    { target: '#table-tour-bill', title: t('tourTableBillTitle'), content: t('tourTableBillContent') },
  ]

  return (
    <>
      <SectionTour sectionId="waiter-table-detail" steps={tourSteps} ready={!!sessionData} />
      <div className="flex justify-between items-start mb-6">
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-4 p-5 pb-0">
            <Link to={'/waiter/tables'}>
              <Button
                variant="ghost"
                className=" h-13 w-13 rounded-full bg-gray-100 hover:bg-gray-200"
              >
                <ArrowLeft className="w-5 h-5" />
              </Button>
            </Link>
            <h1 className="text-4xl font-bold">
              {t('tableHeading', { number: sessionData?.tableNumber ?? '' })}
            </h1>
            {sessionData?.isOccupied ? (
              <Badge className="flex items-center gap-2 p-4 text-1xl">
                <div className="w-4 h-4 bg-[#f3f1f1] rounded-full"></div>{t('statusOccupied')}
              </Badge>
            ) : (
              ''
            )}
          </div>
          <div className="flex items-center gap-2 text-gray-500 pl-9">
            <User className="w-6 h-6" />
            <span className="text-m">{sessionData?.waiterId} {t('waiterRoleLabel')}</span>
          </div>
        </div>
        <div id="table-tour-actions" className="flex items-center gap-3 pr-7">
          <Button
            variant="secondary"
            className="rounded-full bg-gray-100 hover:bg-gray-200 text-1xl px-6 h-18"
          >
            <Printer className="w-4 h-4 mr-2" /> {t('printBillLabel')}
          </Button>
          <Button
            variant="secondary"
            className="rounded-full bg-gray-100 hover:bg-gray-200 text-1xl px-6 h-18"
          >
            <ArrowRightLeft className="w-4 h-4 mr-2" /> {t('transferLabel')}
          </Button>
          {/* Botón principal rojo */}
          <Button className="rounded-full bg-[#8B0000] hover:bg-[#700000] text-1xl text-white px-6 h-18">
            <Plus className="w-4 h-4 mr-2" /> {t('addItemLabel')}
          </Button>
        </div>
      </div>
      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 flex flex-col gap-6">
          <Card id="table-tour-orders" className="rounded-3xl border-none shadow-sm relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-1 bg-linear-to-r from-transparent via-[#8B0000] to-transparent opacity-20"></div>
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-2xl text-gray-800 font-bold">
                {t('orderDetailsTitle')}
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3 max-h-87.5 overflow-y-auto pr-2">
              {itemsToWaiter && itemsToWaiter.length > 0 ? (
                itemsToWaiter.map((item) => {
                  const isSentToKitchen =
                    item.status === 'PREPARING' ||
                    item.status === 'READY' ||
                    item.status === 'DELIVERED'
                  return (
                  <div
                    key={item.id}
                    className="flex items-center justify-between p-4 bg-gray-50/80 rounded-2xl"
                  >
                    <div className="flex items-center gap-4">
                      <div
                        className="w-10 h-10 rounded-full bg-gray-200/60 flex items-center
                                    justify-center text-sm font-bold text-gray-500"
                      >
                        1X
                      </div>
                      <div className="flex flex-col">
                        <span className="font-semibold text-gray-800">
                          {item.name}
                        </span>
                        <span className="text-sm text-gray-400">
                          {item.participantName}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center justify-center gap-4">
                      <span className="font-bold text-gray-700">
                        ${item.price?.toFixed(2)}
                      </span>
                      <Button
                        className=""
                        variant={'destructive'}
                        disabled={isSentToKitchen}
                        title={isSentToKitchen ? t('cannotRemoveSentItem') : undefined}
                        onClick={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          openModal('DELETE_ITEMS', {
                            sessionId: id,
                            itemId: item.id
                          })
                        }}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  </div>
                  )
                })
              ) : (
                <div className="text-center py-8 text-gray-400">
                  {t('noOrdersRegistered')}
                </div>
              )}
            </CardContent>
          </Card>

          <Card id="table-tour-participants" className="rounded-3xl border-none shadow-sm relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-1 bg-linear-to-r from-transparent via-[#8B0000] to-transparent opacity-20"></div>
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-2xl text-gray-800 font-bold">
                {t('participantsTitle')}
              </CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-4">
              {sessionData?.participants &&
              sessionData.participants.length > 0 ? (
                sessionData.participants.map((participant) => (
                  <div
                    key={participant.userId}
                    className="mb-3 shadow-sm rounded-3xl"
                  >
                    <div className="bg-gray-100 rounded-3xl p-3 flex items-center gap-3">
                      <div className="bg-red-100 rounded-full w-10 h-10 flex items-center justify-center">
                        <User className="text-red-700" />
                      </div>
                      <div>
                        <span className="font-semibold text-gray-800">
                          {participant.name}
                        </span>
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="col-span-2 text-center py-8 text-gray-400">
                  {t('noUsersInTable')}
                </div>
              )}
            </CardContent>
          </Card>

          <Card id="table-tour-activity" className="rounded-3xl border-none shadow-sm relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-1 bg-linear-to-r from-transparent via-[#8B0000] to-transparent opacity-20"></div>
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-xs font-bold text-gray-500 tracking-widest uppercase">
                {t('activityTitle')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="ml-3 border-l-2 border-gray-200 pl-5 flex flex-col gap-6 pt-2 max-h-87.5 overflow-y-auto ">
                {sessionData?.activityLog && sessionData.activityLog.length > 0
                  ? sessionData.activityLog.map((activity, index) => (
                      <div key={index} className="relative">
                        <div
                          className={`absolute -left-6.25 top-1.5 w-2.5 h-2.5 rounded-full ${
                            activity.type === 'ITEM_DELETED'
                              ? 'bg-gray-400'
                              : 'bg-[#8B0000]'
                          }`}
                        ></div>
                        <div className="flex flex-col gap-2">
                          <span className="text-xs text-gray-700 font-medium">
                            {activity.type === 'ITEM_DELETED'
                              ? t('itemDeletedLabel', { itemName: activity.itemName ?? '' })
                              : activity.itemName}
                          </span>
                          <span className="text-xs text-gray-400">
                            {activity.type === 'ITEM_DELETED'
                              ? t('deletedLabel')
                              : t('orderPlacedLabel')}
                            : {activity.timestamp}
                          </span>
                        </div>
                      </div>
                    ))
                  : ''}
                <div className="relative">
                  <div className="absolute -left-6.25 top-1.5 w-2.5 h-2.5 rounded-full bg-gray-300"></div>
                  <div className="flex flex-col gap-2 pb-3">
                    <span className="text-xs text-gray-700 font-medium">
                      {t('tableOpenedAtLabel', { timestamp: sessionData?.createdAt ?? '' })}
                    </span>
                    <span className="text-xs text-gray-400">
                      {t('waiterIdLabel', { waiterId: sessionData?.waiterId ?? '' })}
                    </span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
        <div id="table-tour-bill" className="lg:col-span-1">
          <Card>
            <CardHeader className="p-7 border-b border flex flex-row items-center justify-between">
              <CardTitle className="text-2xl text-gray-800 font-bold">
                {billData ? t('billTitle') : t('summaryTitle')}
              </CardTitle>
              {billData && !billData.splits.some((s) => s.status !== 'UNPAID') && (
                <Button
                  variant="ghost"
                  className="text-sm text-destructive"
                  onClick={() => openModal('VOID_BILL', { billId: billData.id, sessionId: id })}
                >
                  <Ban className="w-4 h-4 mr-2" /> {t('voidBillLabel')}
                </Button>
              )}
            </CardHeader>
            {billData ? (
              <>
                <CardContent className="flex flex-col gap-3 max-h-100 overflow-y-auto">
                  {billData.splits.map((split) => {
                    const pendingDigital = billData.pendingDigitalPayments?.find(
                      (p) => p.participantName === split.participantName
                    )
                    return (
                      <div
                        key={split.participantName}
                        className="flex items-center justify-between p-4 bg-gray-50/80 rounded-2xl"
                      >
                        <div className="flex flex-col">
                          <span className="font-semibold text-gray-800">
                            {split.participantName}
                          </span>
                          <span className="text-sm text-gray-500">
                            ${split.amount?.toFixed(2)}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          {split.status === 'UNPAID' && (
                            <Button
                              variant="ghost"
                              size="icon"
                              title={t('removeDinerLabel')}
                              onClick={() =>
                                redistributeSplitMutation.mutate(split.participantName!)
                              }
                              disabled={redistributeSplitMutation.isPending}
                            >
                              <UserMinus className="w-4 h-4" />
                            </Button>
                          )}
                          {split.status === 'PAID' || split.status === 'PARTIALLY_PAID' ? (
                            <div className="flex items-center gap-2">
                              <Badge className="flex items-center gap-1">
                                <CheckCircle2 className="w-4 h-4" />
                                {split.status === 'PAID' ? t('paidLabel') : t('partiallyPaidLabel')}
                              </Badge>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() =>
                                  openModal('REFUND_PAYMENT', {
                                    billId: billData.id,
                                    sessionId: id,
                                    participantName: split.participantName,
                                  })
                                }
                              >
                                <RotateCcw className="w-4 h-4" />
                              </Button>
                            </div>
                          ) : pendingDigital ? (
                            <Button
                              className="text-sm"
                              onClick={() =>
                                confirmDigitalPaymentMutation.mutate(pendingDigital.id)
                              }
                              disabled={confirmDigitalPaymentMutation.isPending}
                            >
                              <CreditCard className="w-4 h-4 mr-2" /> {t('confirmDigitalButton')}
                            </Button>
                          ) : (
                            <Button
                              variant="secondary"
                              className="text-sm"
                              onClick={() =>
                                physicalPaymentMutation.mutate({
                                  participantName: split.participantName!,
                                  amount: split.amount!,
                                })
                              }
                              disabled={physicalPaymentMutation.isPending}
                            >
                              <Banknote className="w-4 h-4 mr-2" /> {t('markPaidButton')}
                            </Button>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </CardContent>
                <CardFooter className="flex flex-col gap-3">
                  <div className="flex justify-between text-xl text-gray-500 p-4 w-full">
                    <span className="text-2xl font-bold">{t('totalLabel')}</span>
                    <span className="text-3xl font-bold text-[#8B0000]">
                      ${billData.total.toFixed(2)}
                    </span>
                  </div>
                  <Button
                    variant="secondary"
                    className="w-full"
                    onClick={() => settleAndCloseMutation.mutate()}
                    disabled={settleAndCloseMutation.isPending}
                  >
                    {settleAndCloseMutation.isPending
                      ? t('closingTableLabel')
                      : t('settleAndCloseButton')}
                  </Button>
                </CardFooter>
              </>
            ) : (
              <>
                <CardContent>
                  <div className="flex justify-between text-xl text-gray-500 pt-4 pl-4 pr-4">
                    <span>{t('subtotalLabel')}</span>
                    <span className="text-xl font-bold text-[#8B0000]">
                      ${subtotal.toFixed(2)}
                    </span>
                  </div>
                  <div className="flex justify-between text-xl text-gray-500 p-4">
                    <span>{t('taxesLabel')}</span>
                    <span className="text-xl font-bold text-[#8B0000]">
                      ${taxes.toFixed(2)}
                    </span>
                  </div>
                </CardContent>
                <CardFooter className="flex flex-col gap-3">
                  <div className="flex justify-between text-xl text-gray-500 p-4 w-full">
                    <span className="text-2xl font-bold">{t('totalLabel')}</span>
                    <span className="text-3xl font-bold text-[#8B0000]">
                      ${total.toFixed(2)}
                    </span>
                  </div>
                  {hasItems ? (
                    <Button
                      className="w-full h-15 text-2xl font-bold"
                      disabled={!hasBillableItems}
                      onClick={() =>
                        openModal('CHARGE_TABLE', {
                          sessionId: id,
                          participantCount: sessionData?.participants?.length ?? 1,
                        })
                      }
                    >
                      {hasBillableItems
                        ? t('chargeMesaLabel')
                        : t('waitingForDeliveryLabel')}
                    </Button>
                  ) : (
                    <Button
                      className="w-full h-15 text-2xl font-bold"
                      onClick={() => {
                        mutation.mutate(id!)
                      }}
                      disabled={mutation.isPending}
                    >
                      {mutation.isPending ? t('closingTableLabel') : t('closeTableButton')}
                    </Button>
                  )}
                </CardFooter>
              </>
            )}
          </Card>
        </div>
        <GlobalDeleteModal/>
        <ChargeTableModal/>
        <VoidBillModal />
        <RefundPaymentModal />
      </div>
    </>
  )
}
