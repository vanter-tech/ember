import { useSessionStore } from '@/store/sessionStore'

const getInitials = (name: string) => {
  if (!name) return '?'
  const words = name.trim().split(' ')
  if (words.length >= 2) {
    return (words[0][0] + words[1][0]).toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
}

const AvatarColors = [
  'bg-zinc-800 text-white border-zinc-900',
  'bg-zinc-200 text-zinc-700 border-white',
  'bg-zinc-300 text-zinc-800 border-white',
]

export const ParticipantsPopUp = () => {
  const { id: tableId, participants } = useSessionStore()
  if (!tableId || !participants) return null
  const visibleParticipants = participants.slice(0, 3)
  const remainingParticipants = participants.length - 3

  return (
    <div className="bg-white rounded-full shadow-lg p-1.5 pr-5 flex items-center gap-3 w-fit pointer-events-auto cursor-pointer hover:bg-zinc-50 transition-colors border border-zinc-100">
      <div className="flex -space-x-3 ml-1">
        {visibleParticipants.map((participant, index) => (
          <div
            key={index}
            className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 ${AvatarColors[index % AvatarColors.length]}`}
          >
            {getInitials(participant.name?.toString() || '')}
          </div>
        ))}
        {remainingParticipants > 0 && (
          <div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 bg-zinc-200 text-zinc-700 border-white">
            +{remainingParticipants}
          </div>
        )}
      </div>
      <div className="flex flex-col justify-center">
        <span className="text-sm font-semibold text-zinc-800 leading-none mb-1">
          Partipantes en la mesa
        </span>
        <span className="text-[11px] text-zinc-500 leading-none font-medium">
          {participants.length} {participants.length === 1 ? 'Persona' : 'Personas'}
        </span>
      </div>
    </div>
  )
}
