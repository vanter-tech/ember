import { settingStore } from '@/store/settingStore'
import { DashboardService } from '@/lib/api'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useState } from 'react'
import { Armchair, User, Users } from 'lucide-react'

export const Tables = () => {
  const { settings } = settingStore()
  const { restaurantId } = useAuthStore()
  const [selectedTable, setSelectedTable] = useState<String | undefined>(
    undefined
  )

  const { data: dashboardData, isPending: isLoadingDashboard } = useQuery({
    queryKey: ['dashboardData', restaurantId],
    queryFn: () => DashboardService.getDashboardData(restaurantId ?? ''),
    enabled: !!restaurantId,
  })

  if (isLoadingDashboard) {
    return <div className="p-6 text-zinc-500">Cargando datos del panel...</div>
  }
  return (
    <div className="flex w-full h-full gap-5 p-5">
      <div className="flex-7">
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
                  <Users className="h-4 w-4" />{table.isOccupied ? table.currentSession?.currentParticipant : '0'}
                </div>
              </CardHeader>
              <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
                {!table.isOccupied && <Armchair className='text-zinc-400 w-7 h-7'/>}
              </div>

              {table.isOccupied && <CardContent className="p-4"></CardContent>}
            </Card>
          ))}
        </div>
        <div className="flex-3"></div>
      </div>
    </div>
  )
}
