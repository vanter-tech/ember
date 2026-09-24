export const AvatarInitials = (name: string) => {
  if (!name) return '?'
  const words = name.trim().split(' ')
  if (words.length >= 2) {
    return (words[0][0] + words[1][0]).toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
}

export const getColorForTable = (sessionId: string) => {
  if(!sessionId) return tablesColor[0]
  let sum = 0
  for(let i = 0; i < sessionId.length; i++){
    sum += sessionId.charCodeAt(i)
  }
  const index = sum % tablesColor.length
  return tablesColor[index]

}

export const tablesColor = [
  'border-orange-500 ',
  'border-amber-500 ',  
  'border-lime-600 ',     
  'border-stone-500 ',   
  'border-rose-500 '
]


const AVATAR_PALETTE = [
  'bg-rose-200 text-rose-800 border-white',
  'bg-orange-200 text-orange-800 border-white',
  'bg-amber-200 text-amber-800 border-white',
  'bg-lime-200 text-lime-800 border-white',
  'bg-emerald-200 text-emerald-800 border-white',
  'bg-teal-200 text-teal-800 border-white',
  'bg-sky-200 text-sky-800 border-white',
  'bg-indigo-200 text-indigo-800 border-white',
  'bg-violet-200 text-violet-800 border-white',
  'bg-fuchsia-200 text-fuchsia-800 border-white',
]

/** Deterministic per-person avatar colors: the same seed (id or name) always maps to the same palette entry. */
export const getAvatarColor = (seed?: string | null) => {
  if (!seed) return AVATAR_PALETTE[0]
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) >>> 0
  }
  return AVATAR_PALETTE[hash % AVATAR_PALETTE.length]
}
