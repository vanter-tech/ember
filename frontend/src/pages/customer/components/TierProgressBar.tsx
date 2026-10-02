import { useTranslation } from '@/lib/i18n'

/** How far the diner is toward the next loyalty tier, as a thin bar under the points-to-go text. */
export const TierProgressBar = ({ percent }: { percent?: number }) => {
  const { t } = useTranslation('customer')
  const value = Math.min(100, Math.max(0, Math.round(percent ?? 0)))

  return (
    <div
      role="progressbar"
      aria-label={t('loyaltyTierProgressAria')}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={value}
      className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-[#8c1717]/10"
    >
      <div className="h-full rounded-full bg-[#8c1717]" style={{ width: `${value}%` }} />
    </div>
  )
}
