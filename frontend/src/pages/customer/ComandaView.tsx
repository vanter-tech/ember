import { useEffect, useMemo } from 'react'
import { useSessionStore } from '@/store/sessionStore'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  ArrowLeft,
  Minus,
  Plus,
  Send,
  Trash,
} from 'lucide-react'

import { AvatarInitials, AvatarColors } from '@/components/AvatarInitials'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Link } from 'react-router-dom'
import { SessionTableService } from '@/lib/api'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { useTranslation } from '@/lib/i18n'

type SessionItem = NonNullable<
  ReturnType<typeof useSessionStore.getState>['items']
>[number]

const groupByParticipant = (itemsToGroup: SessionItem[]) => {
  const dicc = itemsToGroup.reduce(
    (acum, item) => {
      const pId = item.participantId || ''

      if (!acum[pId]) {
        acum[pId] = {
          name: item.participantName,

          subtotal: 0,

          platillos: [],
        }
      }

      const platillosExistentes = acum[pId].platillos.find(
        (itemSave: typeof item) => itemSave.itemId === item.itemId
      )

      if (platillosExistentes) {
        platillosExistentes.cantidad += 1

        acum[pId].subtotal += item.price ?? 0
      } else {
        acum[pId].platillos.push({
          ...item,

          cantidad: 1,
        })

        acum[pId].subtotal += item.price ?? 0
      }

      return acum
    },
    {} as Record<string, { name?: string; subtotal: number; platillos: (SessionItem & { cantidad: number })[] }>
  )

  return Object.values(dicc)
}

export const ComandaView = () => {
  const items = useSessionStore((state) => state.items || [])
  const currentId = useAuthStore((state) => state.userId)
  const sessionId = useSessionStore((state) => state.id)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { t } = useTranslation('customer')

  const Participants = useMemo(
    () => groupByParticipant(items.filter((item) => item.status === 'DRAFT')),
    [items]
  )

  const Historial = useMemo(
    () => groupByParticipant(items.filter((item) => item.status !== 'DRAFT')),
    [items]
  )

  const mutation = useMutation({
    mutationFn: ({
      sessionId,
      itemId,
    }: {
      sessionId: string
      itemId: string
    }) => SessionTableService.deleteItem(sessionId, itemId),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['deleteItem'] })
      toast.success(t('itemDeletedToast'))
    },

    onError: () => {
      toast.error(t('itemDeleteErrorToast'))
    },
  })

  const confirmItemsMutation = useMutation({
    mutationFn: ({
      sessionId,
      currentId,
    }: {
      sessionId: string
      currentId: string
    }) => SessionTableService.confirmMyOrders(sessionId, currentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['confirmItem'] })
      toast.success(t('comandaSentToast'))
    },

    onError: () => {
      toast.error(t('comandaSendErrorToast'))
    },
  })

  useEffect(() => {
    if (items.length === 0) {
      navigate('/customer/menu', { replace: true })
    }
  }, [items.length, navigate])

  const tableSubTotal = items.reduce((acum, item) => acum + (item.price ?? 0), 0)

  const services = tableSubTotal * 0.1

  const total = tableSubTotal + services

  const tengoBorradores = items.some(
    (item) => item.participantId === currentId && item.status === 'DRAFT'
  )

  return (
    <>
      <div className="p-6 pt-0 bg-slate-50">
        <header className="flex flex-row gap-5 pb-5 border-b-2">
          <div className="flex items-center gap-3">
            <Link to={'/customer/menu'}>
              <Button className="w-15 h-15 rounded-full">
                <ArrowLeft className="w-5 h-5" />
              </Button>
            </Link>
          </div>

          <div className="flex flex-col">
            <h2 className="text-2xl text-[#8c1717] font-bold uppercase">
              {t('comandaTitle')}
            </h2>

            <p className="text-md text-gray-500 mt-1">{t('comandaTableLabel')}</p>
          </div>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 pt-6">
          <div className="lg:col-span-3 grid grid-cols-1 md:grid-cols-2 gap-6">
          {Participants.length === 0 && (
            <div className="md:col-span-2 text-center py-12 text-gray-400">
              {t('comandaEmptyDrafts')}
            </div>
          )}
          {Participants.map((person, index) => (
            <Card className="relative overflow-hidden" key={index}>
              <CardHeader>
                <CardTitle className="flex justify-between items-center">
                  <div className="flex flex-row gap-4 p-5 items-center">
                    <div
                      className={`w-11 h-11 p-4 rounded-full flex items-center justify-center text-xs font-bold border-2 ${AvatarColors[index % AvatarColors.length]}`}
                    >
                      {AvatarInitials(person.name ?? '')}
                    </div>

                    <div className="flex flex-col gap-1 items-start">
                      <h2 className="text-2xl font-bold ">{person.name}</h2>

                      {index === 0 ? (
                        <Badge className="p-3 text-sm">{t('comandaHost')}</Badge>
                      ) : (
                        <Badge className="p-3 text-sm">{t('comandaParticipant')}</Badge>
                      )}
                    </div>
                  </div>

                  <div className="flex gap-2 flex-col items-start">
                    <h2 className="text-sm text-gray-500 mt-1">{t('comandaSubtotalLabel')}</h2>

                    <span className="text-lg text-[#8c1717] font-bold">
                      ${person.subtotal.toFixed(2)}
                    </span>
                  </div>
                </CardTitle>
              </CardHeader>

              <CardContent className="overflow-y-auto max-h-87.5 no-scrollbar">
                {person.platillos.map(
                  (item: (typeof items)[0] & { cantidad: number }) => (
                    <div className="flex flex-col gap-2 p-3 border-b-2">
                      <div className="flex justify-between">
                        <span className="text-sm font-bold">
                          {item.name?.toUpperCase()}
                        </span>

                        <span className="text-lg text-[#8c1717] font-bold">
                          ${item.price?.toFixed(2)}
                        </span>
                      </div>

                      {item.modifiers && item.modifiers.length > 0 && (
                        <div className="w-full rounded-md bg-[#8c1717] px-3 py-2 flex flex-col gap-1">
                          {item.modifiers.map((modifier, modifierIndex) => (
                            <div
                              key={modifierIndex}
                              className="flex justify-between text-xs text-white"
                            >
                              <span>{modifier.optionName}</span>

                              {!!modifier.priceDelta && modifier.priceDelta > 0 && (
                                <span>+${modifier.priceDelta.toFixed(2)}</span>
                              )}
                            </div>
                          ))}
                        </div>
                      )}

                      <div className="flex justify-between">
                        <div className='className="  flex items-center gap-3 pb-2'>
                          <Button
                            variant={'destructive'}
                            className="h-8 w-8 cursor-pointer rounded-full p-3 items-center flex"
                          >
                            <Minus className="w-5 h-5" />
                          </Button>

                          <span className="text-xl font-bold">
                            {item.cantidad}
                          </span>

                          <Button className=" h-8 w-8 cursor-pointer rounded-full p-3 items-center flex">
                            <Plus className="w-5 h-5" />
                          </Button>
                        </div>

                        {item.participantId === currentId &&
                          item.status === 'DRAFT' && (
                            <Button
                              variant={'destructive'}
                              disabled={mutation.isPending} 
                              onClick={() => {
                                mutation.mutate({
                                  sessionId: sessionId!,
                                  itemId: item.id!,
                                })
                              }}
                            >
                              <Trash className="w-5 h-5" />
                            </Button>
                          )}
                      </div>
                    </div>
                  )
                )}
              </CardContent>
            </Card>
          ))}
        </div>

        <div className="lg:col-span-1 flex flex-col gap-4">
          <h3 className="text-lg font-bold text-gray-700">{t('comandaHistoryTitle')}</h3>

          {Historial.length === 0 ? (
            <p className="text-sm text-gray-400">
              {t('comandaNoHistory')}
            </p>
          ) : (
            Historial.map((person, index) => (
              <Card className="overflow-hidden" key={index}>
                <CardHeader className="p-4 pb-2">
                  <CardTitle className="flex items-center justify-between text-base">
                    <span className="font-bold">{person.name}</span>
                    <Badge variant="outline">{t('comandaSentBadge')}</Badge>
                  </CardTitle>
                </CardHeader>

                <CardContent className="p-4 pt-0 flex flex-col gap-2">
                  {person.platillos.map(
                    (item: (typeof items)[0] & { cantidad: number }) => (
                      <div
                        key={item.id}
                        className="flex flex-col gap-0.5 text-sm text-gray-600"
                      >
                        <div className="flex justify-between">
                          <span>
                            {item.cantidad}x {item.name}
                          </span>
                          <span>${item.price?.toFixed(2)}</span>
                        </div>
                        {item.modifiers && item.modifiers.length > 0 && (
                          <div className="w-full rounded-md bg-[#8c1717] px-3 py-2 flex flex-col gap-1">
                            {item.modifiers.map((modifier, modifierIndex) => (
                              <div
                                key={modifierIndex}
                                className="flex justify-between text-xs text-white"
                              >
                                <span>{modifier.optionName}</span>

                                {!!modifier.priceDelta && modifier.priceDelta > 0 && (
                                  <span>+${modifier.priceDelta.toFixed(2)}</span>
                                )}
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )
                  )}
                </CardContent>
              </Card>
            ))
          )}
        </div>
        </div>

        <Card className=" p-6 mt-8 flex flex-col md:flex-row justify-between items-center gap-6">
          <div className="flex flex-col gap-3 w-full md:w-120">
            <div className="flex justify-between">
              <h2 className="text-sm text-gray-500 mt-1">{t('comandaSubtotalLabel')}</h2>

              <span>${tableSubTotal.toFixed(2)}</span>
            </div>

            <div className="flex justify-between">
              <h2 className="text-sm text-gray-500 mt-1">{t('comandaServiceLabel')}</h2>

              <span>${services.toFixed(2)}</span>
            </div>

            <div className="flex justify-between border-t pt-2 mt-1">
              <h2 className="text-sm text-gray-700 mt-1 font-bold">{t('comandaTotalLabel')}</h2>

              <span>${total.toFixed(2)}</span>
            </div>
          </div>

          <Button
            className="rounded-full h-auto px-12 py-8 text-xl font-semibold w-full md:w-auto gap-2"
            disabled={!tengoBorradores || confirmItemsMutation.isPending}
            onClick={() => {
              confirmItemsMutation.mutate({
                sessionId: sessionId!,
                currentId: currentId!,
              })
            }}
          >
            <Send className="w-7 h-7" />
            {confirmItemsMutation.isPending ? t('comandaSending') : t('comandaSendToKitchen')}
          </Button>
        </Card>
      </div>
    </>
  )
}
