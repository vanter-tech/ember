import { ParticipantQrModal } from './components/ParticipantsQrModal'
import { DashboardService, SessionTableService } from '@/lib/api'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useState } from 'react'
import { Armchair, Users } from 'lucide-react'
import { useUIStore } from '@/store/uiStore'
import { Link } from 'react-router-dom'

export const Tables = () => {
  const { restaurantId } = useAuthStore()
  const [selectedTable, setSelectedTable] = useState<string | undefined>(
    undefined
  )

  const { openModal } = useUIStore()

  const { data: dashboardData, isPending: isLoadingDashboard } = useQuery({
    queryKey: ['dashboardData', restaurantId],
    queryFn: () => DashboardService.getDashboardData(),
    enabled: !!restaurantId,
  })

  const tableDetails = dashboardData?.find(
    (data) => data.tableId === selectedTable
  )

  const sessionId = tableDetails?.currentSession?.sessionId

  const { data: sessionData } = useQuery({
    queryKey: ['sessionDetails', sessionId],
    queryFn: () => SessionTableService.sessionInformation(sessionId!),
    enabled: !!sessionId,
  })

  const itemsToWaiter = sessionData?.items
    ? sessionData.items.filter((item) => item.status != 'DRAFT')
    : []

  if (isLoadingDashboard) {
    return <div className="p-6 text-zinc-500">Cargando datos del panel...</div>
  }
  return (
    <div className="flex w-full h-full gap-5 p-5">
      <div className="w-[70%]">
        <div className="flex justify-between mb-5">
          <h2>Salon Principal</h2>
          <div className="flex gap-4">
            <div className="flex items-center gap-2">
              <div className="w-5 h-5 bg-[#8c1717] rounded-full"></div>
              <span className="text-md text-zinc-500">Ocupado</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-5 h-5 bg-[#6b6161] rounded-full"></div>
              <span className="text-md text-zinc-500">Libre</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4">
          {dashboardData?.map((table) => (
            <Card
              key={table.tableId}
              onClick={() => setSelectedTable(table.tableId)}
              className={`cursor-pointer shadow-sm border-zinc-100 
                h-40 flex flex-col justify-between rounded-2xl relative
                ${table.isOccupied ? 'border-2 bg-[#8c1717] text-white' : 'bg-white text-black'}`}
            >
              <CardHeader className="p-4 pb-0 flex justify-between">
                <span className=" text-2xl font-bold">
                  M{table.tableNumber}
                </span>
                <div
                  className={`flex items-center justify-center gap-1 rounded-full h-6 w-11 ${table.isOccupied ? 'border-2 bg-[#8b0000] text-white' : 'bg-[#f3f4f6] text-black'}}`}
                >
                  <Users className="h-4 w-4" />
                  {table.isOccupied
                    ? table.currentSession?.currentParticipant
                    : '0'}
                </div>
              </CardHeader>
              <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
                {table.isOccupied ? (
                  <Armchair className="text-white w-7 h-7" />
                ) : (
                  <Armchair className="text-zinc-400 w-7 h-7" />
                )}
              </div>

              {table.isOccupied && <CardContent className="p-4"></CardContent>}
            </Card>
          ))}
        </div>
      </div>
      <div className="w-[30%] border-l border-zinc-200 pl-5">
        {tableDetails ? (
          <div>
            <h2 className="text-xl font-semibold mb-5">Detalles de mesa</h2>
            <div className="bg-white rounded-2xl p-6">
              <div className="flex justify-between items-center">
                <h2 className="text-[#8c1717] font-bold text-3xl">
                  M{tableDetails.tableNumber}
                </h2>
                <div className="flex flex-col gap-2 text-right">
                  <span className="text-xs text-zinc-500">Mesero</span>
                  {tableDetails.currentSession?.waiterName || 'Sin asignar'}
                </div>
              </div>
              <div className="flex items-center gap-2 mt-4">
                {!tableDetails.isOccupied ? (
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#6b6161] rounded-full"></div>
                    <span className="text-md text-zinc-500">Libre</span>
                  </div>
                ) : (
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#8c1717] rounded-full"></div>
                    <span className="text-md text-zinc-500">Ocupado</span>
                  </div>
                )}
              </div>
              <div className="my-6 border-y border-zinc-100 py-4">
                {itemsToWaiter.length > 0 ? (
                  <div className="flex flex-col gap-3 max-h-50 overflow-y-auto pr-1">
                    {itemsToWaiter.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center justify-between"
                      >
                        <div className="flex flex-col">
                          <span className="text-sm font-semibold">
                            {item.name}
                          </span>
                          <span className="text-xs text-zinc-500">
                            {item.participantName}
                          </span>
                        </div>
                        <span className="text-sm font-bold text-[#8c1717]">
                          ${item.price?.toFixed(2)}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <span>No hay pedido actualmente</span>
                )}
              </div>
              <div className="flex flex-col gap-4 mt-6">
                <Button className="w-full text-md">
                  {tableDetails.isOccupied ? 'Cobrar mesa' : 'Abrir mesa'}
                </Button>
                {tableDetails.isOccupied ? (
                  <Link to={tableDetails.currentSession?.sessionId + ''}>
                    <Button className="w-full text-md">Ver Informacion</Button>
                  </Link>
                ) : (
                  ' '
                )}
                <Button variant={'outline'} className="w-full text-md">
                  Imprimir cuenta
                </Button>

                <Button
                  variant={'outline'}
                  className="w-full text-md"
                  onClick={(e) => {
                    openModal('PARTICIPANTS_QR', tableDetails)
                    e.preventDefault()
                    e.stopPropagation()
                  }}
                >
                  Asignar Mesa
                </Button>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex h-full items-center justify-center text-zinc-500">
            Selecciona una mesa para ver los detalles
          </div>
        )}
      </div>
      <ParticipantQrModal />
    </div>
  )
}
