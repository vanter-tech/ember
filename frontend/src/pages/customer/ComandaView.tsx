import { useMemo, useState } from 'react'
import { useSessionStore } from '@/store/sessionStore'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  ArrowLeft,
  ArrowRight,
  Car,
  Minus,
  Plus,
  Send,
  Trash,
  User,
} from 'lucide-react'
import { AvatarInitials, AvatarColors } from '@/components/AvatarInitials'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Link } from 'react-router-dom'
import { api, SessionTableService } from '@/lib/api'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

export const ComandaView = () => {
  const items = useSessionStore((state) => state.items || [])
  const sessionId = useSessionStore((state) => state.id)
  const queryClient = useQueryClient()

  const Participants = useMemo(() => {
    const dicc = items.reduce(
      (acum, item) => {
        const pId = item.participantId || ''
        if (!acum[pId]) {
          acum[pId] = {
            name: item.participantName,
            subtotal: 0,
            platillos: [],
          }
        }

        const platillosExistentes = acum[pId].platillos.find(
          (itemSave: typeof item) => itemSave.itemId === item.itemId
        )

        if (platillosExistentes) {
          platillosExistentes.cantidad += 1
          acum[pId].subtotal += item.price
        } else {
          acum[pId].platillos.push({
            ...item,
            cantidad: 1,
          })
          acum[pId].subtotal += item.price
        }

        return acum
      },
      {} as Record<string, any>
    )

    return Object.values(dicc)
  }, [items])

  const mutation = useMutation({
    mutationFn: ({
      sessionId,
      itemId,
    }: {
      sessionId: string
      itemId: string
    }) => SessionTableService.deleteItem(sessionId, itemId),

    onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['deleteItem'] })
        toast.success('Platillo eliminado')
    },
    onError: (e) => {
        toast.error('Error al eliminar')
    }
  })

  const tableSubTotal = Participants.reduce(
    (acum, item) => (acum += item.subtotal),
    0
  )
  const services = tableSubTotal * 0.1
  const total = tableSubTotal + services

  return (
    <>
      <div className="p-6 pt-0 bg-slate-50">
        <header className="flex flex-row gap-5 pb-5 border-b-2">
          <div className="flex items-center gap-3">
            <Link to={'/customer/menu'}>
              <Button className="w-15 h-15 rounded-full">
                <ArrowLeft className="w-5 h-5" />
              </Button>
            </Link>
          </div>
          <div className="flex flex-col">
            <h2 className="text-2xl text-[#8c1717] font-bold uppercase">
              Revision de Comanda
            </h2>
            <p className="text-md text-gray-500 mt-1">Mesa</p>
          </div>
        </header>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-6">
          {Participants.map((person, index) => (
            <Card className="relative overflow-hidden" key={index}>
              <CardHeader>
                <CardTitle className="flex justify-between items-center">
                  <div className="flex flex-row gap-4 p-5 items-center">
                    <div
                      className={`w-11 h-11 p-4 rounded-full flex items-center justify-center text-xs font-bold border-2 ${AvatarColors[index % AvatarColors.length]}`}
                    >
                      {AvatarInitials(person.name)}
                    </div>
                    <div className="flex flex-col gap-1 items-start">
                      <h2 className="text-2xl font-bold ">{person.name}</h2>
                      {index === 0 ? (
                        <Badge className="p-3 text-sm">Anfitrion</Badge>
                      ) : (
                        <Badge className="p-3 text-sm">Participante</Badge>
                      )}
                    </div>
                  </div>
                  <div className="flex gap-2 flex-col items-start">
                    <h2 className="text-sm text-gray-500 mt-1">Subtotal</h2>
                    <span className="text-lg text-[#8c1717] font-bold">
                      ${person.subtotal.toFixed(2)}
                    </span>
                  </div>
                </CardTitle>
              </CardHeader>
              <CardContent className="overflow-y-auto max-h-87.5 no-scrollbar">
                {person.platillos.map(
                  (item: (typeof items)[0] & { cantidad: number }) => (
                    <div className="flex flex-col gap-2 p-3 border-b-2">
                      <div className="flex justify-between">
                        <span className="text-sm font-bold">
                          {item.name?.toUpperCase()}
                        </span>
                        <span className="text-lg text-[#8c1717] font-bold">
                          ${item.price?.toFixed(2)}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <div className='className="  flex items-center gap-3 pb-2'>
                          <Button
                            variant={'destructive'}
                            className="h-8 w-8 cursor-pointer rounded-full p-3 items-center flex"
                          >
                            <Minus className="w-5 h-5" />
                          </Button>
                          <span className="text-xl font-bold">
                            {item.cantidad}
                          </span>
                          <Button className=" h-8 w-8 cursor-pointer rounded-full p-3 items-center flex">
                            <Plus className="w-5 h-5" />
                          </Button>
                        </div>
                        <Button
                          className=""
                          variant={'destructive'}
                          disabled={mutation.isPending}
                          onClick={() => {
                            mutation.mutate({
                                sessionId: sessionId!,
                                itemId: item.id!
                            })
                          }}
                        >
                          <Trash />
                        </Button>
                      </div>
                    </div>
                  )
                )}
              </CardContent>
            </Card>
          ))}
        </div>
        <Card className=" p-6 mt-8 flex flex-col md:flex-row justify-between items-center gap-6">
          <div className="flex flex-col gap-3 w-full md:w-120">
            <div className="flex justify-between">
              <h2 className="text-sm text-gray-500 mt-1">Subtotal</h2>
              <span>${tableSubTotal.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <h2 className="text-sm text-gray-500 mt-1">Servicio (10%)</h2>
              <span>${services.toFixed(2)}</span>
            </div>
            <div className="flex justify-between border-t pt-2 mt-1">
              <h2 className="text-sm text-gray-700 mt-1 font-bold">Total</h2>
              <span>${total.toFixed(2)}</span>
            </div>
          </div>
          <Button className="rounded-full h-auto px-12 py-8 text-xl font-semibold w-full md:w-auto gap-2">
            <Send className="w-7 h-7" />
            Enviar a cocina
          </Button>
        </Card>
      </div>
    </>
  )
}
