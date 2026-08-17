import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useSessionStore } from '@/store/sessionStore'
import { useAuthStore } from '@/store/authStore'
import { billingService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ArrowLeft, CreditCard, CheckCircle2, Clock } from 'lucide-react'
import toast from 'react-hot-toast'

export const Bill = () => {
  const bill = useSessionStore((state) => state.bill)
  const billSplits = useSessionStore((state) => state.billSplits)
  const sessionId = useSessionStore((state) => state.id)
  const participants = useSessionStore((state) => state.participants)
  const currentId = useAuthStore((state) => state.userId)

  const myName = participants?.find((p) => p.userId === currentId)?.name
  const mySplit = billSplits?.find((split) => split.participantName === myName)

  const payMutation = useMutation({
    mutationFn: () =>
      billingService.initiateDigitalPayment(bill!.id!, myName!, mySplit!.amount!),
    onSuccess: () => {
      toast.success('Pago enviado. Espera la confirmación del mesero.')
    },
    onError: () => toast.error('No se pudo iniciar el pago.'),
  })

  const paymentRequested = payMutation.isSuccess

  return (
    <div className="p-6 pt-0 bg-slate-50 min-h-screen">
      <header className="flex flex-row items-center gap-3 pb-5 border-b-2">
        <Link to={`/customer/menu/${sessionId}/comanda`}>
          <Button className="w-15 h-15 rounded-full">
            <ArrowLeft className="w-5 h-5" />
          </Button>
        </Link>
        <h2 className="text-2xl text-[#8c1717] font-bold uppercase">
          Mi Cuenta
        </h2>
      </header>

      <div className="pt-6 max-w-xl mx-auto flex flex-col gap-4">
        {!bill ? (
          <Card>
            <CardContent className="py-12 text-center text-gray-400">
              Aún no se ha solicitado la cuenta. Pide al mesero que la calcule
              cuando estés listo para pagar.
            </CardContent>
          </Card>
        ) : (
          <>
            <Card>
              <CardHeader>
                <CardTitle className="flex justify-between items-center">
                  <span>Total de la mesa</span>
                  <span className="text-2xl text-[#8c1717] font-bold">
                    ${bill.total?.toFixed(2)}
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                {billSplits?.map((split) => (
                  <div
                    key={split.participantName}
                    className={`flex items-center justify-between p-4 rounded-2xl ${
                      split.participantName === myName
                        ? 'bg-[#8c1717]/5 border-2 border-[#8c1717]/20'
                        : 'bg-gray-50'
                    }`}
                  >
                    <div className="flex flex-col">
                      <span className="font-semibold">
                        {split.participantName}
                        {split.participantName === myName && ' (Tú)'}
                      </span>
                      <span className="text-sm text-gray-500">
                        ${split.amount?.toFixed(2)}
                      </span>
                    </div>
                    {split.paid ? (
                      <Badge className="flex items-center gap-1">
                        <CheckCircle2 className="w-4 h-4" /> Pagado
                      </Badge>
                    ) : (
                      <Badge variant="outline">Pendiente</Badge>
                    )}
                  </div>
                ))}
              </CardContent>
            </Card>

            {mySplit && !mySplit.paid && (
              <Button
                className="w-full h-15 text-xl font-bold gap-2"
                disabled={payMutation.isPending || paymentRequested}
                onClick={() => payMutation.mutate()}
              >
                {paymentRequested ? (
                  <>
                    <Clock className="w-5 h-5" /> Esperando confirmación...
                  </>
                ) : (
                  <>
                    <CreditCard className="w-5 h-5" /> Pagar mi parte ($
                    {mySplit.amount?.toFixed(2)})
                  </>
                )}
              </Button>
            )}
          </>
        )}
      </div>
    </div>
  )
}
