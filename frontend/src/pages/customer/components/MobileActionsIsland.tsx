import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, MoreHorizontal, Receipt, Users } from 'lucide-react'
import { ArrowRight, MoreHorizontal, Users } from 'lucide-react'
import { useSessionStore } from '@/store/sessionStore'
import { ParticipantsList } from './ParticipantsList'

export const MobileActionsIsland = () => {
  const { id: tableId, participants } = useSessionStore()
  const navigate = useNavigate()
  const [isOpen, setIsOpen] = useState(false)
  const [showParticipants, setShowParticipants] = useState(false)

  if (!tableId) return null

  const close = () => {
    setIsOpen(false)
    setShowParticipants(false)
  }

  return (
    <div className="relative">
      {isOpen && (
        <div className="absolute bottom-full right-0 mb-3 w-56 bg-white rounded-2xl shadow-xl border border-zinc-100 p-2 z-50">
          {showParticipants ? (
            <ParticipantsList participants={participants ?? []} />
          ) : (
            <div className="flex flex-col gap-1">
              <button
                type="button"
                className="flex items-center gap-3 p-2 rounded-xl text-left transition-colors hover:bg-zinc-50"
                onClick={() => setShowParticipants(true)}
              >
                <Users className="h-4 w-4 text-zinc-500" />
                <span className="text-sm font-medium text-zinc-700">
                  Ver participantes
                </span>
              </button>
              <button
                type="button"
                className="flex items-center gap-3 p-2 rounded-xl text-left transition-colors hover:bg-zinc-50"
                onClick={() => {
                  close()
                  navigate(`${tableId}/comanda`)
                }}
              >
                <ArrowRight className="h-4 w-4 text-zinc-500" />
                <span className="text-sm font-medium text-zinc-700">
                  Ver comanda
                </span>
              </button>
              <button
                type="button"
                className="flex items-center gap-3 p-2 rounded-xl text-left transition-colors hover:bg-zinc-50"
                onClick={() => {
                  close()
                  navigate(`${tableId}/bill`)
                }}
              >
                <Receipt className="h-4 w-4 text-zinc-500" />
                <span className="text-sm font-medium text-zinc-700">
                  Ver cuenta
                </span>
              </button>
            </div>
          )}
        </div>
      )}

      <button
        type="button"
        className="flex h-14 w-14 items-center justify-center rounded-full border border-zinc-200 bg-white text-zinc-600 shadow-2xl transition-colors hover:bg-zinc-50"
        onClick={() => (isOpen ? close() : setIsOpen(true))}
        aria-label="Opciones de mesa"
      >
        <MoreHorizontal className="h-6 w-6" />
      </button>
    </div>
  )
}
