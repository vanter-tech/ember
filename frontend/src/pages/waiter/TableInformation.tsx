import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { ArrowLeft, User, Printer, ArrowRightLeft, Plus } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import toast from 'react-hot-toast'
import { useNavigate} from 'react-router-dom'
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

export const TableInformation = () => {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const { data: sessionData, isPending: isLoadingData } = useQuery({
    queryKey: ['sessionDetails', id],
    queryFn: () => SessionTableService.sessionInformation(id!),
  })

  const mutation = useMutation({
    mutationFn: SessionTableService.closeEmptySession,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessionDetails'] })
      toast.success('Mesa cerrada.')
      navigate('/waiter/tables')
    }
  })

  if (isLoadingData) {
    return <div className="p-6 text-zinc-500">Cargando datos del panel...</div>
  }

  const hasItems = sessionData?.items && sessionData.items.length > 0

  const subtotal =
    sessionData?.items?.reduce((total, item) => total + (item.price ?? 0), 0) ??
    0
  const taxes = subtotal * 0.1
  const tip = subtotal * 0.15
  const total = taxes + tip + subtotal

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
              <CardTitle className="text-2xl text-gray-800 font-bold">
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

          <Card className="rounded-3xl border-none shadow-sm relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-1 bg-linear-to-r from-transparent via-[#8B0000] to-transparent opacity-20"></div>
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-2xl text-gray-800 font-bold">
                Participantes
              </CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-4">
              {sessionData?.participants &&
              sessionData.participants.length > 0 ? (
                sessionData.participants.map((participant) => (
                  <div key={participant.userId} className="mb-3 shadow-sm rounded-3xl">
                    <div className="bg-gray-100 rounded-3xl p-3 flex items-center gap-3">
                      <div className="bg-red-100 rounded-full w-10 h-10 flex items-center justify-center">
                        <User className="text-red-700" />
                      </div>
                      <div>
                        <span className="font-semibold text-gray-800">
                          {participant.name}
                        </span>
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="col-span-2 text-center py-8 text-gray-400">
                  No hay usuarios en esta mesa.
                </div>
              )}
            </CardContent>
          </Card>

          <Card className="rounded-3xl border-none shadow-sm relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-1 bg-linear-to-r from-transparent via-[#8B0000] to-transparent opacity-20"></div>
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-xs font-bold text-gray-500 tracking-widest uppercase">
                Actividad
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="ml-3 border-l-2 border-gray-200 pl-5 flex flex-col gap-6 pt-2">
                {sessionData?.items && sessionData.items.length > 0
                  ? sessionData.items.map((item) => (
                      <div key={item.id} className="relative">
                        <div className="absolute -left-6.25 top-1.5 w-2.5 h-2.5 rounded-full bg-[#8B0000]"></div>
                        <div className="flex flex-col gap-2">
                          <span className="text-xs text-gray-700 font-medium">
                            {item.name}
                          </span>
                          <span className="text-xs text-gray-400">
                            Order hecha: {item.addedAt}
                          </span>
                        </div>
                      </div>
                    ))
                  : ''}
                <div className="relative">
                  <div className="absolute -left-6.25 top-1.5 w-2.5 h-2.5 rounded-full bg-gray-300"></div>
                  <div className="flex flex-col gap-2 pb-3">
                    <span className="text-xs text-gray-700 font-medium">
                      Mesa abierta: {sessionData?.createdAt}
                    </span>
                    <span className="text-xs text-gray-400">
                      Camerero: {sessionData?.waiterId}
                    </span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
        <div className="lg:col-span-1">
          <Card>
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-2xl text-gray-800 font-bold">
                Resumen
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex justify-between text-xl text-gray-500 pt-4 pl-4 pr-4">
                <span>Subtotal</span>
                <span className="text-xl font-bold text-[#8B0000]">
                  ${subtotal.toFixed(2)}
                </span>
              </div>
              <div className="flex justify-between text-xl text-gray-500 pt-4 pl-4 pr-4">
                <span>Taxes (10%)</span>
                <span className="text-xl font-bold text-[#8B0000]">
                  ${taxes.toFixed(2)}
                </span>
              </div>
              <div className="flex justify-between text-xl text-gray-500 p-4">
                <span>Propina (15%)</span>
                <span className="text-xl font-bold text-[#8B0000]">
                  ${tip.toFixed(2)}
                </span>
              </div>
            </CardContent>
            <CardFooter className="flex flex-col gap-3">
              <div className="flex justify-between text-xl text-gray-500 p-4 w-full">
                <span className="text-2xl font-bold">Total</span>
                <span className="text-3xl font-bold text-[#8B0000]">
                  ${total.toFixed(2)}
                </span>
              </div>
              {hasItems ? (
                <Button className="w-full h-15 text-2xl font-bold"
                >
                  Cobrar Mesa
                </Button>
              ) : (
                <Button className="w-full h-15 text-2xl font-bold"
                onClick={() => {
                  mutation.mutate(id!)
                }}
                disabled={mutation.isPending}
                >
                  {mutation.isPending ? 'Cerrando' : 'Cerrar mesa'}
                </Button>
              )}
            </CardFooter>
          </Card>
        </div>
      </div>
    </>
  )
}
