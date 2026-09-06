import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Utensils,
  QrCode,
  Users,
  Wallet,
  History,
  LifeBuoy,
  ImageIcon,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { JoinTableModal } from './components/JoinTableModal'
import { BannerPickerModal } from './components/BannerPickerModal'
import { useUIStore } from '@/store/uiStore'
import { loyaltyAccountService, userProfileService } from '@/lib/api'
import { BANNER_PRESETS, resolveBannerKey } from '@/lib/bannerPresets'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { useState, type MouseEvent } from 'react'
import emberLogo from '@/assets/ember.png'

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
  const [bannerPickerOpen, setBannerPickerOpen] = useState(false)

  const openJoinModal = (e: MouseEvent) => {
    openModal('JOIN_TABLE')
    e.preventDefault()
    e.stopPropagation()
  }

  const { data: profile } = useQuery({
    queryKey: ['me'],
    queryFn: userProfileService.me,
    retry: false,
  })
  const bannerKey = resolveBannerKey(profile?.bannerKey)

  // A successful fetch means this customer is already bound to a restaurant; a failure
  // (no tenant yet) is expected and just hides the visits card.
  const { data: visits, isSuccess: hasTenant } = useQuery({
    queryKey: ['loyaltyVisits', 'me'],
    queryFn: loyaltyAccountService.visits,
    retry: false,
  })

  const steps = [
    { Icon: QrCode, title: t('homeStep1Title'), body: t('homeStep1Body') },
    { Icon: Users, title: t('homeStep2Title'), body: t('homeStep2Body') },
    { Icon: Wallet, title: t('homeStep3Title'), body: t('homeStep3Body') },
  ]

  return (
    <>
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        {/* Banner — ~40% of the viewport. The gradient/pattern here is what the future
            placeholder-image picker will swap out. */}
        <Card className="relative min-h-[40vh] overflow-hidden rounded-3xl border-none shadow-md">
          <div className={`absolute inset-0 ${BANNER_PRESETS[bannerKey].gradient}`} />
          <div
            className="absolute inset-0 opacity-[0.15]"
            style={{
              backgroundImage:
                'radial-gradient(circle at 1px 1px, white 1px, transparent 0)',
              backgroundSize: '22px 22px',
            }}
          />
          <button
            type="button"
            aria-label={t('bannerPickerAria')}
            onClick={() => setBannerPickerOpen(true)}
            className="absolute right-3 top-3 z-10 grid h-9 w-9 place-items-center rounded-full bg-white/15 text-white backdrop-blur-sm transition-colors hover:bg-white/25"
          >
            <ImageIcon className="h-4 w-4" />
          </button>
          <CardContent className="relative flex min-h-[40vh] flex-col items-center justify-center gap-5 p-8 text-center text-white">
            <img
              src={emberLogo}
              alt=""
              className="h-24 w-24 object-contain drop-shadow-xl md:h-32 md:w-32"
            />
            <div className="space-y-1">
              <p className="text-sm text-white/80">{t('homeWelcomeBack')}</p>
              <h1 className="text-2xl font-bold md:text-3xl">
                {name || t('homeGuestName')}
              </h1>
            </div>
            <Button
              className="h-12 rounded-2xl bg-white px-6 text-base font-semibold text-[#8c1717] hover:bg-white/90"
              onClick={openJoinModal}
            >
              <Utensils className="mr-2 h-5 w-5" />
              {t('homeJoinTableCtaShort')}
            </Button>
          </CardContent>
        </Card>

        {/* Remaining space: 1 column on mobile, 2 on desktop. */}
        <div className="grid gap-4 md:grid-cols-2">
          <Card className="rounded-3xl border border-gray-100 shadow-sm md:col-span-2">
            <CardHeader>
              <CardTitle className="text-lg">{t('homeHowItWorksTitle')}</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-5 sm:grid-cols-3">
              {steps.map(({ Icon, title, body }, i) => (
                <div key={i} className="flex flex-col gap-2">
                  <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[#8c1717]/10 text-[#8c1717]">
                    <Icon className="h-5 w-5" />
                  </div>
                  <span className="font-semibold text-gray-900">{title}</span>
                  <span className="text-sm text-gray-500">{body}</span>
                </div>
              ))}
            </CardContent>
          </Card>

          {hasTenant && (
            <Card className="rounded-3xl border border-gray-100 shadow-sm">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-lg">
                  <History className="h-5 w-5 text-[#8c1717]" />
                  {t('loyaltyVisitsTitle')}
                </CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                {!visits || visits.length === 0 ? (
                  <p className="py-6 text-center text-sm text-gray-400">
                    {t('loyaltyNoVisitsRegistered')}
                  </p>
                ) : (
                  visits.map((visit, index) => (
                    <div
                      key={index}
                      className="flex flex-col gap-1 rounded-2xl bg-gray-50 p-3 transition-colors hover:bg-[#8c1717]/5"
                    >
                      <span className="text-xs text-gray-500">
                        {visit.visitedAt ? formatVisitDate(visit.visitedAt) : '—'}
                      </span>
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-semibold">
                          {visit.amountPaid != null
                            ? formatCurrency(visit.amountPaid)
                            : '—'}
                        </span>
                        <span className="text-xs font-semibold text-[#8c1717]">
                          {t('loyaltyVisitPoints', { points: visit.pointsEarned! })}
                        </span>
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          )}

          <Card
            className={`rounded-3xl border border-gray-100 shadow-sm ${
              hasTenant ? '' : 'md:col-span-2'
            }`}
          >
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <LifeBuoy className="h-5 w-5 text-[#8c1717]" />
                {t('homeHelpTitle')}
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col items-start gap-3">
              <p className="text-sm text-gray-500">{t('homeHelpBody')}</p>
              <Button
                variant="outline"
                className="rounded-2xl border-[#8c1717]/30 text-[#8c1717] hover:bg-[#8c1717] hover:text-white"
                onClick={openJoinModal}
              >
                {t('homeHelpCta')}
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
      <JoinTableModal />
      <BannerPickerModal
        open={bannerPickerOpen}
        onClose={() => setBannerPickerOpen(false)}
        current={bannerKey}
      />
    </>
  )
}
