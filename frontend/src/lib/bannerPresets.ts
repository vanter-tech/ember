// Customer-home banner presets. The backend only stores/validates the key
// (see backend BannerKey enum); the visual is all here.

export const BANNER_KEYS = [
  'ember',
  'sunset',
  'forest',
  'ocean',
  'midnight',
  'mono',
] as const

export type BannerKey = (typeof BANNER_KEYS)[number]

export const DEFAULT_BANNER: BannerKey = 'ember'

export const BANNER_PRESETS = {
  ember: {
    labelKey: 'bannerEmber',
    gradient: 'bg-gradient-to-br from-[#8c1717] via-[#7a1414] to-[#3b0a0a]',
  },
  sunset: {
    labelKey: 'bannerSunset',
    gradient: 'bg-gradient-to-br from-[#b3541e] via-[#c1666b] to-[#4a1942]',
  },
  forest: {
    labelKey: 'bannerForest',
    gradient: 'bg-gradient-to-br from-[#1e3a2b] via-[#2d5a3d] to-[#0f2417]',
  },
  ocean: {
    labelKey: 'bannerOcean',
    gradient: 'bg-gradient-to-br from-[#0b3a5b] via-[#1b6ca8] to-[#08243a]',
  },
  midnight: {
    labelKey: 'bannerMidnight',
    gradient: 'bg-gradient-to-br from-[#1e1b3a] via-[#3a2d5a] to-[#0f0f24]',
  },
  mono: {
    labelKey: 'bannerMono',
    gradient: 'bg-gradient-to-br from-zinc-700 via-zinc-800 to-zinc-950',
  },
} as const satisfies Record<BannerKey, { labelKey: string; gradient: string }>

export const resolveBannerKey = (value: string | null | undefined): BannerKey =>
  (BANNER_KEYS as readonly string[]).includes(value ?? '')
    ? (value as BannerKey)
    : DEFAULT_BANNER
