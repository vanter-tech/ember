import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PlanSkeleton } from './PlanSkeleton'
import { AlertTriangle, Check } from 'lucide-react'
import { restaurantAdminService, type SubscriptionResponse } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { useTranslation } from '@/lib/i18n'
import { dictionaries } from '@/locales'

type AdminKey = keyof (typeof dictionaries)['es']['admin']

const CONTACT_URL = 'https://ember.vanter.net/contacto'
const RENEW_SOON_DAYS = 10
const DAY_MS = 86_400_000

// Mirrors what PlanGateService enforces (tables, periodfilters, cashclose, roles, branding, export)
// plus the core features and support levels every plan advertises on the landing.
const PLAN_FEATURES: Record<SubscriptionResponse['plan'], AdminKey[]> = {
  FREE: ['planFeatureTables1', 'planFeatureCollabCart', 'planFeatureKds', 'planFeatureAnalyticsDay', 'planFeatureSupportCommunity'],
  STARTER: [
    'planFeatureEverythingInFree',
    'planFeatureTables10',
    'planFeatureAnalyticsPeriods',
    'planFeatureCashClose',
    'planFeatureRoles',
    'planFeatureBranding',
    'planFeatureSupportEmail',
  ],
  PRO: ['planFeatureEverythingInStarter', 'planFeatureTablesUnlimited', 'planFeatureExport', 'planFeatureSupportPriority'],
  ENTERPRISE: [
    'planFeatureEverythingInPro',
    'planFeatureSupport247',
    'planFeatureSla',
    'planFeatureIntegrations',
    'planFeatureAccountManager',
  ],
}

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })

/** Read-only subscription summary. The operator records the dates by hand; no prices are shown. */
export const PlanSettings = () => {
  const { t } = useTranslation('admin')
  const { data: subscription, isPending } = useQuery({
    queryKey: ['subscription'],
    queryFn: restaurantAdminService.getSubscription,
  })

  // Read once on mount (not during render) so the countdown is stable between re-renders.
  const [now] = useState(() => Date.now())

  if (isPending) return <PlanSkeleton label={t('loadingSettingsLabel')} />
  if (!subscription) return null

  const daysLeft = subscription.planPeriodEnd
    ? Math.ceil((new Date(subscription.planPeriodEnd).getTime() - now) / DAY_MS)
    : null
  const expired = daysLeft !== null && daysLeft < 0
  const renewSoon = daysLeft !== null && daysLeft <= RENEW_SOON_DAYS

  const daysLabel =
    daysLeft === null
      ? null
      : daysLeft < 0
        ? t('planExpiredDaysAgo', { days: -daysLeft })
        : daysLeft === 0
          ? t('planExpiresToday')
          : t('planDaysLeft', { days: daysLeft })

  const row = (label: string, value: string) => (
    <div className="flex flex-col gap-0.5">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="font-medium text-foreground">{value}</span>
    </div>
  )

  return (
    <Card className="py-6">
      <CardHeader>
        <CardTitle className="text-xl">{t('planCardTitle')}</CardTitle>
        <CardDescription>{t('planCardDescription')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-stretch sm:justify-between">
          <div className="flex flex-col justify-center gap-4">
            <div className="flex flex-col gap-1">
              <span className="text-sm text-muted-foreground">{t('planCurrentLabel')}</span>
              <span className="text-4xl font-bold tracking-tight text-[#8c1717]">{subscription.plan}</span>
            </div>
            <div className="flex flex-col items-start gap-2">
              <span className="text-sm text-muted-foreground">{t('planStatusLabel')}</span>
              <Badge
                className={
                  subscription.status === 'ACTIVE'
                    ? 'border-transparent bg-[#8c1717] px-6 py-2 text-base font-semibold text-white'
                    : 'px-6 py-2 text-base font-semibold'
                }
                variant={subscription.status === 'ACTIVE' ? 'default' : 'outline'}
              >
                {subscription.status === 'ACTIVE' ? t('planStatusActive') : t('planStatusInactive')}
              </Badge>
            </div>
          </div>

          {subscription.planPeriodEnd && (
            <div className="flex flex-col gap-1 rounded-2xl border border-[#8c1717]/15 bg-[#8c1717]/5 px-6 py-4 sm:min-w-64 sm:text-right">
              <span className="text-sm text-muted-foreground">{t('planRenewalLabel')}</span>
              <span className="text-2xl font-bold text-foreground">{formatDate(subscription.planPeriodEnd)}</span>
              {daysLabel && <span className="text-sm text-muted-foreground">{daysLabel}</span>}
            </div>
          )}
        </div>

        <div className="flex flex-wrap gap-x-12 gap-y-4 border-t pt-5">
          {subscription.planStartedAt && row(t('planStartedLabel'), formatDate(subscription.planStartedAt))}
          {subscription.billingPeriod &&
            row(
              t('planPeriodLabel'),
              subscription.billingPeriod === 'ANNUAL'
                ? t('planPeriodAnnual')
                : subscription.billingPeriod === 'SEMESTRAL'
                  ? t('planPeriodSemestral')
                  : t('planPeriodMonthly'),
            )}
        </div>

        <div className="flex flex-col gap-3 border-t pt-5">
          <h3 className="text-sm font-semibold text-foreground">{t('planIncludesTitle')}</h3>
          <ul className="grid grid-cols-1 gap-x-8 gap-y-2 sm:grid-cols-2">
            {PLAN_FEATURES[subscription.plan].map((key) => (
              <li key={key} className="flex items-center gap-2 text-sm text-foreground">
                <Check size={16} className="shrink-0 text-emerald-600" /> {t(key)}
              </li>
            ))}
          </ul>
        </div>

        {renewSoon && (
          <div
            className={`flex items-start gap-3 rounded-xl border p-4 text-sm ${
              expired ? 'border-red-300 bg-red-50 text-red-900' : 'border-amber-300 bg-amber-50 text-amber-900'
            }`}
          >
            <AlertTriangle size={18} className="mt-0.5 shrink-0" />
            <span>{expired ? t('planExpiredNotice') : t('planRenewSoon')}</span>
          </div>
        )}
      </CardContent>
      <CardFooter className="flex-col items-stretch justify-end gap-3 sm:flex-row sm:items-center">
        <p className="text-sm text-muted-foreground sm:text-right">{t('planManualNote')}</p>
        <Button asChild>
          <a href={CONTACT_URL} target="_blank" rel="noreferrer">
            {t('planContactButton')}
          </a>
        </Button>
      </CardFooter>
    </Card>
  )
}
