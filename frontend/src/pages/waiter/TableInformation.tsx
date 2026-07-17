import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { ArrowLeft, User, Printer, ArrowRightLeft, Plus } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

export const TableInformation = () => {
  const { id } = useParams()
  const { data: sessionData, isPending: isLoadingData } = useQuery({
    queryKey: ['sessionDetails', id],
    queryFn: () => SessionTableService.sessionInformation(id!),
  })

  if (isLoadingData) {
    return <div className="p-6 text-zinc-500">Cargando datos del panel...</div>
  }

  return (
    <>
      <div className="flex justify-between items-start mb-6">
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-4 p-5 pb-0">
            <Button
              variant="ghost"
              className=" h-13 w-13 rounded-full bg-gray-100 hover:bg-gray-200"
            >
              <ArrowLeft className="w-5 h-5" />
            </Button>
            <h1 className="text-4xl font-bold">
              Mesa M{sessionData?.tableNumber}
            </h1>
            {sessionData?.isOccupied ? (
              <Badge className="flex items-center gap-2 p-4 text-1xl">
                <div className="w-4 h-4 bg-[#f3f1f1] rounded-full"></div>Ocupado
              </Badge>
            ) : (
              ''
            )}
          </div>
          <div className="flex items-center gap-2 text-gray-500 pl-9">
            <User className="w-6 h-6" />
            <span className="text-m">{sessionData?.waiterId} (CAMARERO)</span>
          </div>
        </div>
        <div className="flex items-center gap-3 pr-7">
          <Button
            variant="secondary"
            className="rounded-full bg-gray-100 hover:bg-gray-200 text-1xl w-38 h-18"
          >
            <Printer className="w-4 h-4 mr-2" /> Print Bill
          </Button>
          <Button
            variant="secondary"
            className="rounded-full bg-gray-100 hover:bg-gray-200 text-1xl w-38 h-18"
          >
            <ArrowRightLeft className="w-4 h-4 mr-2" /> Transfer
          </Button>
          {/* Botón principal rojo */}
          <Button className="rounded-full bg-[#8B0000] hover:bg-[#700000] text-1xl text-white w-38 h-18">
            <Plus className="w-4 h-4 mr-2" /> Add Item
          </Button>
        </div>
      </div>
      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 flex flex-col gap-6">
          <Card className="rounded-3xl border-none shadow-sm relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-1 bg-linear-to-r from-transparent via-[#8B0000] to-transparent opacity-20"></div>
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-2xl text-gray-800">
                Detalles de pedidos
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {sessionData?.items && sessionData.items.length > 0 ? (
                sessionData.items.map((item) => (
                  <div
                    key={item.id}
                    className="flex items-center justify-between p-4 bg-gray-50/80 rounded-2xl"
                  >
                    <div className="flex items-center gap-4">
                      <div
                        className="w-10 h-10 rounded-full bg-gray-200/60 flex items-center
                                    justify-center text-sm font-bold text-gray-500"
                      >
                        1X
                      </div>
                      <div className="flex flex-col">
                        <span className="font-semibold text-gray-800">
                          {item.name}
                        </span>
                        <span className="text-sm text-gray-400">
                          {item.participantName}
                        </span>
                      </div>
                    </div>
                    <span className="font-bold text-gray-700">
                      ${item.price?.toFixed(2)}
                    </span>
                  </div>
                ))
              ) : (
                <div className="text-center py-8 text-gray-400">
                  No hay pedidos registrados en esta mesa.
                </div>
              )}
            </CardContent>
          </Card>
        </div>
        <div className="lg:col-span-1"></div>
      </div>
    </>
  )
}
