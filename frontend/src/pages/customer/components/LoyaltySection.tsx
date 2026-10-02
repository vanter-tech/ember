import { Lock, Sparkles } from 'lucide-react'
import type { LoyaltyAccountResponse } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { TIER_LABELS } from '@/pages/admin/components/settings/loyalty/types'
import { useTranslation } from '@/lib/i18n'
import { useLoyaltyAccount } from './useLoyaltyAccount'
import { TierProgressBar } from './TierProgressBar'

type Reward = NonNullable<LoyaltyAccountResponse['rewards']>[number]

interface RewardCardProps {
  reward: Reward
  className?: string
}

export const RewardCard = ({ reward, className = '' }: RewardCardProps) => {
  const { t } = useTranslation('customer')

  return (
    <Card
      className={`rounded-3xl ${reward.unlocked ? 'border-[#8c1717]/30' : 'opacity-60'} ${className}`}
    >
      <CardContent className="p-4 flex flex-col gap-2">
        <div className="flex items-start justify-between gap-2">
          <span className="font-semibold">{reward.name}</span>
          {!reward.unlocked && <Lock className="w-4 h-4 text-gray-400 shrink-0" />}
        </div>
        {reward.description && (
          <p className="text-sm text-gray-500">{reward.description}</p>
        )}
        <Badge variant={reward.unlocked ? 'default' : 'outline'} className="w-fit">
          {reward.unlocked
            ? t('loyaltyRewardUnlocked')
            : t('loyaltyRewardLocked', { tierName: TIER_LABELS[reward.requiredTier!] })}
        </Badge>
      </CardContent>
    </Card>
  )
}

interface RewardsListProps {
  rewards: Reward[]
}

export const RewardsList = ({ rewards }: RewardsListProps) => (
  <div className="flex flex-row gap-3 overflow-x-auto pb-2">
    {rewards.map((reward) => (
      <RewardCard key={reward.id} reward={reward} className="shrink-0 w-64" />
    ))}
  </div>
)

// Rewards are catalog-display only (no points cost, no redemption flow — see
// LoyaltyReward.java) so this only ever shows what's unlocked vs. locked by tier.
export const LoyaltySection = () => {
  const { t } = useTranslation('customer')
  const { loyaltyEnabled, account } = useLoyaltyAccount()

  if (!loyaltyEnabled || !account) return null

  const showPoints = (account.totalPoints ?? 0) > 0
  const rewards = account.rewards ?? []
  const showRewards = rewards.length > 0

  if (!showPoints && !showRewards) return null

  return (
    <div className="flex flex-col gap-3 px-4">
      {showPoints && (
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

      {showRewards && (
        <div className="hidden sm:flex sm:flex-col gap-2">
          <h2 className="text-sm font-semibold text-gray-500">{t('loyaltyRewardsTitle')}</h2>
          <RewardsList rewards={rewards} />
        </div>
      )}
    </div>
  )
}
