export const AvatarInitials = (name: string) => {
  if (!name) return '?'
  const words = name.trim().split(' ')
  if (words.length >= 2) {
    return (words[0][0] + words[1][0]).toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
}

export const AvatarColors = [
  'bg-zinc-800 text-white border-zinc-900',
  'bg-zinc-200 text-zinc-700 border-white',
  'bg-zinc-300 text-zinc-800 border-white',
]
