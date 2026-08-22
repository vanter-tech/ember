import { AvatarInitials, AvatarColors } from '@/components/AvatarInitials'
import type { participantDTO } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

interface ParticipantsListProps {
  participants: participantDTO[]
}

export const ParticipantsList = ({ participants }: ParticipantsListProps) => {
  const { t } = useTranslation('customer')
  return (
    <>
      <div className="px-2 pb-2 mb-2 border-b border-zinc-100 flex justify-between items-center">
        <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider">
          {t('participantsListTitle')}
        </span>
        <span className="text-xs font-medium text-zinc-500 bg-zinc-100 px-2 py-0.5 rounded-full">
          {participants.length}{' '}
        </span>
      </div>
      <div className="flex flex-col gap-1 max-h-48 overflow-y-auto">
        {participants.map((participant, index) => (
          <div
            key={participant.userId || index}
            className="flex items-center gap-3 p-2 rounded-xl hover:bg-zinc-50 transition-colors cursor-pointer"
          >
            <div
              className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 ${AvatarColors[index % AvatarColors.length]}`}
            >
              {AvatarInitials(participant.name?.toString() || '')}
            </div>
            <span className="text-sm font-medium text-zinc-700 truncate">
              {participant.name || t('participantsGuestFallback')}
            </span>
          </div>
        ))}
      </div>
    </>
  )
}
