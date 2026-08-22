import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Utensils, Sparkles, History, Store } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { JoinTableModal } from './components/JoinTableModal'
import { useUIStore } from '@/store/uiStore'
import { loyaltyAccountService } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import { TIER_LABELS, TIER_BADGE_CLASSNAMES } from '@/pages/admin/components/settings/loyalty/types'
import { useTranslation } from '@/lib/i18n'
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
  const { t } = useTranslation('customer')

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
                <Avatar className="h-24 w-24 md:h-40 md:w-40 border-2 border-gray-100">
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
                  {t('homeBio')}
                </p>
              </div>
            </div>
            <div className="w-full md:w-auto flex justify-end">
              <Button
                className="w-full h-14 md:h-20 rounded-3xl md:w-auto hover:bg-[#660000] px-6 md:px-8 py-4 md:py-6 text-base md:text-xl font-semibold transition-colors"
                onClick={openJoinModal}
              >
                <Utensils className="mr-2 h-5 w-5" />
                {t('homeJoinTableCta')}
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
      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <Card className="md:col-span-4 border border-gray-100 shadow-sm bg-white rounded-3xl transition-all duration-200 hover:shadow-md hover:border-[#8c1717]/20">
          <CardContent className="flex flex-col gap-6 md:flex-row items-center justify-between p-6">
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
                <p className="text-sm text-gray-500">{t('homeWelcomeBack')}</p>
              </div>
            </div>
            <Button
              variant="outline"
              className="rounded-2xl border-[#8c1717]/30 text-[#8c1717] hover:bg-[#8c1717] hover:text-white transition-colors"
              onClick={openJoinModal}
            >
              <Utensils className="mr-2 h-4 w-4" />
              {t('homeJoinTableCtaShort')}
            </Button>
          </CardContent>
        </Card>

        {loyaltyAccount && (
          <Card className="md:col-span-3 bg-[#8c1717]/5 border-2 border-[#8c1717]/20 rounded-3xl transition-all duration-200 hover:shadow-lg hover:border-[#8c1717]/40">
            <CardContent className="flex flex-col gap-4 p-6">
              {loyaltyAccount.restaurantName && (
                <div className="flex items-center gap-1.5 text-xs font-medium text-[#8c1717]/70">
                  <Store className="w-3.5 h-3.5" />
                  {t('loyaltyProgramAt', { restaurantName: loyaltyAccount.restaurantName! })}
                </div>
              )}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
                <div className="flex items-center gap-3">
                  <Sparkles className="w-8 h-8 text-[#8c1717] shrink-0" />
                  <div className="flex flex-col">
                    <span className="text-2xl font-bold text-gray-900">
                      {loyaltyAccount.totalPoints}
                    </span>
                    <span className="text-sm text-gray-500">{t('loyaltyPointsLabel')}</span>
                  </div>
                </div>
                <div className="flex flex-col justify-center gap-2 sm:border-x sm:border-[#8c1717]/10 sm:px-6">
                  <Badge className={`w-fit ${TIER_BADGE_CLASSNAMES[loyaltyAccount.tier!]}`}>
                    {TIER_LABELS[loyaltyAccount.tier!]}
                  </Badge>
                  {loyaltyAccount.nextTier ? (
                    <>
                      <div className="h-2 w-full rounded-full bg-[#8c1717]/10 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-[#8c1717] transition-all duration-500"
                          style={{ width: `${loyaltyAccount.tierProgressPercent ?? 0}%` }}
                        />
                      </div>
                      <span className="text-sm text-gray-500">
                        {t('loyaltyPointsToNextTier', {
                          points: loyaltyAccount.pointsToNextTier!,
                          tierName: TIER_LABELS[loyaltyAccount.nextTier],
                        })}
                      </span>
                    </>
                  ) : (
                    <span className="text-sm text-gray-500">{t('loyaltyMaxTierReached')}</span>
                  )}
                </div>
                <div className="flex flex-col justify-center gap-1">
                  <span className="text-xs uppercase tracking-wide text-gray-400">
                    {t('loyaltyLastVisitLabel')}
                  </span>
                  <span className="text-sm font-semibold text-gray-700">
                    {lastVisitDate ? formatVisitDate(lastVisitDate) : t('loyaltyNoVisitsYet')}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        <Card className="md:col-span-1 border border-gray-100 shadow-sm bg-white rounded-3xl transition-all duration-200 hover:shadow-md hover:border-[#8c1717]/20">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <History className="w-5 h-5 text-[#8c1717]" />
              {t('loyaltyVisitsTitle')}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {!visits || visits.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-6">
                {t('loyaltyNoVisitsRegistered')}
              </p>
            ) : (
              visits.map((visit, index) => (
                <div
                  key={index}
                  className="flex flex-col gap-1 p-3 rounded-2xl bg-gray-50 transition-colors hover:bg-[#8c1717]/5"
                >
                  <span className="text-xs text-gray-500">
                    {visit.visitedAt ? formatVisitDate(visit.visitedAt) : '—'}
                  </span>
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-sm">
                      {visit.amountPaid != null ? formatCurrency(visit.amountPaid) : '—'}
                    </span>
                    <span className="text-xs text-[#8c1717] font-semibold">
                      {t('loyaltyVisitPoints', { points: visit.pointsEarned! })}
                    </span>
                  </div>
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
