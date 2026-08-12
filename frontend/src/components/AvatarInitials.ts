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

export const AvatarColors = [
  'bg-zinc-800 text-white border-zinc-900',
  'bg-zinc-200 text-zinc-700 border-white',
  'bg-zinc-300 text-zinc-800 border-white',
]
