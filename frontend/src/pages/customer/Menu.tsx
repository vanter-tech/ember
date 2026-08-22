import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { menuServices, SessionTableService } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import toast from 'react-hot-toast'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardTitle,
  CardHeader,
} from '@/components/ui/card'
import { useEffect, useState } from 'react'
import { ArrowLeft, Plus, Receipt } from 'lucide-react'
import { useWebsocketStore } from '@/store/websocket'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { useTranslation } from '@/lib/i18n'
import { useSessionStore } from '@/store/sessionStore'
import { ParticipantsPopUp } from '@/pages/customer/components/ParticipantsPopUp'
import { ItemsFloatingIsland } from './components/ItemsFloatingIsland'
import { MobileActionsIsland } from './components/MobileActionsIsland'
import { useNavigate } from 'react-router-dom'

export const Menu = () => {
  const [activeCategory, setActiveCategory] = useState<Number | undefined>()
  const { connect, isConnected, subscribeToSession, stompClient } =
    useWebsocketStore()
  const sessionId = useSessionStore((state) => state.id)
  const joinCode = useSessionStore((state) => state.joinCode)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t } = useTranslation('customer')

  const mutation = useMutation({
    mutationFn: async ({
      sessionId,
      itemId,
    }: {
      sessionId: string
      itemId: number
    }) => {
      if (!sessionId) throw new Error('No session ID available')
      return SessionTableService.addItem(sessionId, itemId)
    },
    onSuccess: () => {
      toast.success('Item added successfully!')
    },
    onError: () => {
      toast.error('Failed to add item. Please try again.')
    },
  })

  const {
    data: menuItems = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['digital-menu'],
    queryFn: () => menuServices.getMenu(),
  })

  const itemsCategory =
    menuItems.find((item) => item.id == activeCategory)?.items || []

  const {
    data: sessionData,
    isError: isSessionError,
    isLoading: isSessionLoading
  } = useQuery({
    queryKey: ['sessionStatus', sessionId],
    queryFn: () => SessionTableService.sessionStatus(sessionId!),
    enabled: !!sessionId,
    retry: false
  })

  useEffect(() => {
    if(isSessionLoading) return
    if(sessionId && (isSessionError || sessionData?.status === 'CLOSED')){
      useSessionStore.getState().clearSession()
      queryClient.removeQueries({queryKey: ['sessionStatus', sessionId]})
      navigate('/customer/home')
    }
  },[sessionId, isSessionError, isSessionLoading, navigate, sessionData])
    


  useEffect(() => {
    if (menuItems.length > 0 && activeCategory === undefined) {
      setActiveCategory(menuItems[0].id)
    }
  }, [menuItems, activeCategory])

  useEffect(() => {
    if (sessionId && isConnected && stompClient?.connected) {
      subscribeToSession(sessionId)
    }
  }, [isConnected, sessionId, connect, subscribeToSession, stompClient])


  if (isLoading)
    return <div className="p-6 text-zinc-500">{t('loadingItems')}</div>
  if (isError)
    return (
      <div className="p-6 text-red-500">{t('loadingItemsError')}</div>
    )

  return (
    <>
      <div className="p-2">
        <div className="flex items-center w-full h-20 justify-items-start shadow-sm rounded-3xl p-4 gap-4">
          <Button className=" h-13 w-13 rounded-full hover:bg-gray-200">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1 className="text-3xl font-bold text-[#8c1717] tracking-tight">
            Ember
          </h1>
          <LanguageSwitcher />
        </div>
        <div className="flex flex-col gap-4 p-4">
          <div className="flex items-center justify-between p-4">
            <div className=" flex flex-col">
              <h1 className="text-3xl font-bold">{t('menuTitle')}</h1>
              <p className="text-sm text-gray-500 mt-1">
                {t('menuSubtitle')}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Badge className="p-6 text-md font-bold flex gap-3">
                {' '}
                {t('tableCodeLabel', { code: joinCode ?? '' })}
              </Badge>
              <Button
                variant="secondary"
                className="rounded-full h-13 px-5"
                onClick={() => navigate(`${sessionId}/bill`)}
              >
                <Receipt className="w-4 h-4 mr-2" /> {t('viewBillLabel')}
              </Button>
            </div>
          </div>
          <div className="flex flex-row gap-3 p-2 pb-5 border-b overflow-x-auto">
            {menuItems.map((categories) => (
              <div
                className={`w-auto shadow-sm p-4 rounded-3xl cursor-pointer 
              shrink-0 ${activeCategory === categories.id ? 'bg-[#8c1717] text-white' : 'bg-white text-[#8c1717]'}`}
                onClick={() => setActiveCategory(categories.id)}
                key={categories.id}
              >
                {categories.name}
              </div>
            ))}
          </div>
        </div>
        <div className="p-4 grid grid-cols-1 md:grid-cols-4 lg:grid-cols-4 gap-4">
          {itemsCategory.map((item, index) => (
            <Card
              key={item.id}
              className={`rounded-4xl shadow-sm hover:shadow-md transition-shadow relative overflow-hidden min-h-75 ${index === 0 ? 'md:col-span-2 md:row-span-2' : ''} ${index === 1 ? 'md:col-span-2 md:row-span-1' : ''} `}
            >
              <img
                src={item.imageUrl}
                alt={item.name}
                className="absolute inset-0 w-full h-full object-cover z-0 "
              />
              <div className="absolute inset-0 bg-linear-to-t from-black/90 via-black/40 to-transparent z-10"></div>
              <div className="absolute inset-0 flex justify-between items-end p-4 z-20 text-white">
                <Badge
                  className="absolute top-4 left-4 px-3 py-1 text-lg p-5
                font-bold text-[#8c1717] bg-white rounded-full shadow-md"
                >
                  ${item.price?.toFixed(2)}
                </Badge>
                <CardHeader className="p-3">
                  <CardTitle className="text-2xl font-bold ">
                    {item.name}
                  </CardTitle>
                  <CardDescription>{item.description}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col items-end gap-2 p-0">
                  <Button
                    className="bg-[#8c1717] hover:bg-[#8c1717]/90 text-white p-7 rounded-full shadow-md"
                    onClick={() =>
                      mutation.mutate({
                        sessionId: sessionId ?? '',
                        itemId: item.id ?? 0,
                      })
                    }
                  >
                    <Plus className="w-5 h-5" />
                  </Button>
                </CardContent>
              </div>
            </Card>
          ))}
        </div>
        <div className="fixed bottom-24 md:bottom-10 left-11 z-50 hidden sm:block">
          <ParticipantsPopUp />
        </div>
        <div className="fixed bottom-24 md:bottom-10 right-11 z-50 hidden sm:block">
          <ItemsFloatingIsland />
        </div>
        <div className="fixed bottom-24 right-6 z-50 sm:hidden">
          <MobileActionsIsland />
        </div>
      </div>
    </>
  )
}
