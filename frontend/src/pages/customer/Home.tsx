import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Utensils, Sparkles, History } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { JoinTableModal } from './components/JoinTableModal'
import { useUIStore } from '@/store/uiStore'
import { loyaltyAccountService } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import { TIER_LABELS, TIER_BADGE_CLASSNAMES } from '@/pages/admin/components/settings/loyalty/types'
import type { MouseEvent } from 'react'

const formatVisitDate = (isoDateTime: string) =>
  new Date(isoDateTime).toLocaleDateString('es-ES', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })

export const Home = () => {
  const { name } = useAuthStore()
  const { openModal } = useUIStore()

  const openJoinModal = (e: MouseEvent) => {
    openModal('JOIN_TABLE')
    e.preventDefault()
    e.stopPropagation()
  }

  const {
    data: visits,
    isSuccess: hasTenant,
    isLoading: visitsLoading,
  } = useQuery({
    queryKey: ['loyaltyVisits', 'me'],
    queryFn: loyaltyAccountService.visits,
    retry: false,
  })

  const { data: loyaltyAccount } = useQuery({
    queryKey: ['loyaltyAccount', 'me'],
    queryFn: loyaltyAccountService.me,
    enabled: hasTenant,
    retry: false,
  })

  const showDashboard = hasTenant && !visitsLoading
  const lastVisitDate = visits && visits.length > 0 ? visits[0].visitedAt : undefined

  if (!showDashboard) {
    return (
      <>
        <Card className="w-full border-none shadow-sm bg-white rounded-2xl">
          <CardContent className="flex flex-col gap-6 md:flex-row items-center justify-evenly p-6 ">
            <div className="flex items-center gap-5 w-full md:w-auto">
              <div className="relative">
                <Avatar className="h-40 w-40 border-2 border-gray-100">
                  <AvatarImage
                    src="https://i.pravatar.cc/150?u=alejandra"
                    alt="Alejandra"
                  />
                  <AvatarFallback>AG</AvatarFallback>
                </Avatar>
              </div>
              <div className="flex flex-col">
                <h2 className="text-2xl font-bold text-gray-900">{name}</h2>
                <p className="text-sm text-gray-500 mt-1">
                  Amante de la gastronomia y mas cosas.
                </p>
              </div>
            </div>
            <div className="w-full md:w-auto flex justify-end">
              <Button
                className="w-full h-20  rounded-3xl md:w-auto hover:bg-[#660000] px-8 py-6 text-xl font-semibold transition-colors"
                onClick={openJoinModal}
              >
                <Utensils className="mr-2 h-5 w-5" />
                Entrar a una mesa.
              </Button>
            </div>
          </CardContent>
        </Card>
        <JoinTableModal />
      </>
    )
  }

  return (
    <>
      <div className="flex flex-col gap-4">
        <Card className="w-full border-none shadow-sm bg-white rounded-2xl">
          <CardContent className="flex items-center justify-between gap-4 p-6">
            <div className="flex items-center gap-4">
              <Avatar className="h-16 w-16 border-2 border-gray-100">
                <AvatarImage
                  src="https://i.pravatar.cc/150?u=alejandra"
                  alt="Alejandra"
                />
                <AvatarFallback>AG</AvatarFallback>
              </Avatar>
              <div className="flex flex-col">
                <h2 className="text-xl font-bold text-gray-900">{name}</h2>
                <p className="text-sm text-gray-500">Bienvenido de vuelta.</p>
              </div>
            </div>
            <Button variant="outline" className="rounded-2xl" onClick={openJoinModal}>
              <Utensils className="mr-2 h-4 w-4" />
              Entrar a una mesa
            </Button>
          </CardContent>
        </Card>

        {loyaltyAccount && (
          <Card className="bg-[#8c1717]/5 border-2 border-[#8c1717]/20 rounded-2xl">
            <CardContent className="py-5 flex items-center gap-3">
              <Sparkles className="w-8 h-8 text-[#8c1717] shrink-0" />
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <span className="font-semibold">{loyaltyAccount.totalPoints} pts</span>
                  <Badge className={TIER_BADGE_CLASSNAMES[loyaltyAccount.tier!]}>
                    {TIER_LABELS[loyaltyAccount.tier!]}
                  </Badge>
                </div>
                {loyaltyAccount.nextTier && (
                  <span className="text-sm text-gray-500">
                    {loyaltyAccount.pointsToNextTier} pts para {TIER_LABELS[loyaltyAccount.nextTier]}
                  </span>
                )}
                {lastVisitDate && (
                  <span className="text-xs text-gray-400">
                    Última visita: {formatVisitDate(lastVisitDate)}
                  </span>
                )}
              </div>
            </CardContent>
          </Card>
        )}

        <Card className="border-none shadow-sm bg-white rounded-2xl">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <History className="w-5 h-5 text-[#8c1717]" />
              Tus visitas
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {!visits || visits.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-6">
                Aún no tienes visitas registradas.
              </p>
            ) : (
              visits.map((visit, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between p-4 rounded-2xl bg-gray-50"
                >
                  <span className="text-sm text-gray-600">
                    {visit.visitedAt ? formatVisitDate(visit.visitedAt) : '—'}
                  </span>
                  <span className="font-semibold">
                    {visit.amountPaid != null ? formatCurrency(visit.amountPaid) : '—'}
                  </span>
                  <span className="text-sm text-[#8c1717] font-semibold">
                    +{visit.pointsEarned} pts
                  </span>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
      <JoinTableModal />
    </>
  )
}
