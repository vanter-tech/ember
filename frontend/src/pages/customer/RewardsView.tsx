import { Link } from 'react-router-dom'
import { ArrowLeft, Sparkles } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { RewardCard } from './components/LoyaltySection'
import { useLoyaltyAccount } from './components/useLoyaltyAccount'
import { TIER_LABELS } from '@/pages/admin/components/settings/loyalty/types'
import { useTranslation } from '@/lib/i18n'
import { TierProgressBar } from './components/TierProgressBar'

export const RewardsView = () => {
  const { t } = useTranslation('customer')
  const { account } = useLoyaltyAccount()
  const rewards = account?.rewards ?? []
  const showPoints = (account?.totalPoints ?? 0) > 0

  return (
    <div className="p-6 pt-0 bg-slate-50 min-h-screen">
      <header className="flex flex-row items-center gap-3 pb-5 border-b-2">
        <Link to="/customer/menu">
          <Button className="w-15 h-15 rounded-full">
            <ArrowLeft className="w-5 h-5" />
          </Button>
        </Link>
        <h2 className="text-2xl text-[#8c1717] font-bold uppercase">
          {t('loyaltyRewardsTitle')}
        </h2>
      </header>

      <div className="pt-6 max-w-2xl mx-auto flex flex-col gap-6">
        {showPoints && account && (
          <Card className="bg-[#8c1717]/5 border-2 border-[#8c1717]/20 rounded-3xl">
            <CardContent className="py-4 flex items-center gap-3">
              <Sparkles className="w-7 h-7 text-[#8c1717] shrink-0" />
              <div className="flex flex-col">
                <span className="font-semibold">
                  {account.totalPoints} {t('loyaltyPointsLabel')}
                </span>
                <span className="text-sm text-gray-500">
                  {account.nextTier
                    ? t('loyaltyPointsToNextTier', {
                        points: account.pointsToNextTier!,
                        tierName: TIER_LABELS[account.nextTier],
                      })
                    : t('loyaltyMaxTierReached')}
                </span>
                {account.nextTier && <TierProgressBar percent={account.tierProgressPercent} />}
              </div>
            </CardContent>
          </Card>
        )}

        {rewards.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-12">
            {t('loyaltyNoRewards')}
          </p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {rewards.map((reward) => (
              <RewardCard key={reward.id} reward={reward} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
