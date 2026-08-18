import type { LoyaltyTier } from '@/lib/api'

export const TIER_LABELS: Record<LoyaltyTier, string> = {
  BRONCE: 'Bronce',
  PLATA: 'Plata',
  ORO: 'Oro',
  PLATINO: 'Platino',
}

export const TIER_BADGE_CLASSNAMES: Record<LoyaltyTier, string> = {
  BRONCE: 'bg-amber-100 text-amber-700',
  PLATA: 'bg-zinc-200 text-zinc-700',
  ORO: 'bg-yellow-100 text-yellow-700',
  PLATINO: 'bg-violet-100 text-violet-700',
}
