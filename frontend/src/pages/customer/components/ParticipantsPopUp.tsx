import { useSessionStore } from '@/store/sessionStore'
import { useState } from 'react'
import { AvatarInitials, AvatarColors } from '@/components/AvatarInitials'

export const ParticipantsPopUp = () => {
  const { id: tableId, participants } = useSessionStore()
  if (!tableId || !participants) return null
  const [isOpen, setIsOpen] = useState(false)
  const visibleParticipants = participants.slice(0, 3)
  const remainingParticipants = participants.length - 3

  return (
    <div className="relative">

      {isOpen && (
        <div className="absolute bottom-full left-0 mb-3 w-56 bg-white rounded-2xl shadow-xl border border-zinc-100 p-2 z-50">
          <div className="px-2 pb-2 mb-2 border-b border-zinc-100 flex justify-between items-center">
            <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider">
              En la mesa
            </span>
            <span className="text-xs font-medium text-zinc-500 bg-zinc-100 px-2 py-0.5 rounded-full">
              {participants.length}{' '}
            </span>
          </div>
          <div className="flex flex-col gap-1 max-h-48 overflow-y-auto">
            {participants.map((participant, index) => (
              <div key={participant.userId || index} 
              className="flex items-center gap-3 p-2 rounded-xl hover:bg-zinc-50 transition-colors cursor-pointer">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 ${AvatarColors[index % AvatarColors.length]}`}>
                  {AvatarInitials(participant.name?.toString() || '')}
                </div>
                <span className="text-sm font-medium text-zinc-700 truncate">
                  {participant.name || 'Invitado'}
                </span>
              </div>
            ))}
          </div>

        </div>
      )}


      <div
        className="bg-white rounded-full shadow-lg p-1.5 pr-5 flex items-center gap-3 w-fit pointer-events-auto cursor-pointer hover:bg-zinc-50 transition-colors border border-zinc-100"
        onClick={() => setIsOpen(!isOpen)}
      >
        <div className="flex -space-x-3 ml-1">
          {visibleParticipants.map((participant, index) => (
            <div
              key={index}
              className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 ${AvatarColors[index % AvatarColors.length]}`}
            >
              {AvatarInitials(participant.name?.toString() || '')}
            </div>
          ))}
          {remainingParticipants > 0 && (
            <div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 bg-zinc-200 text-zinc-700 border-white">
              +{remainingParticipants}
            </div>
          )}
        </div>
        <div className="flex-col justify-center hidden md:flex pr-1">
          <span className="text-sm font-semibold text-zinc-800 leading-none mb-1">
            Partipantes en la mesa
          </span>
          <span className="text-[11px] text-zinc-500 leading-none font-medium">
            {participants.length}{' '}
            {participants.length === 1 ? 'Persona' : 'Personas'}
          </span>
        </div>
      </div>
    </div>
  )
}
