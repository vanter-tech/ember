import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { Keyboard, QrCode } from 'lucide-react'
import { useState } from 'react'
import { OtpInput } from '@/components/ui/otpInput'
import { useMutation } from '@tanstack/react-query'
import { SessionTableService } from '@/lib/api'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'

export const JoinTableModal = () => {
  const { activeModal, closeModal } = useUIStore()
  const [opciones, setOpciones] = useState('MENU')
  const [joinCode, setJoinCode] = useState('')
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: async (joinCode: string) => {
      return await SessionTableService.joinSessionViaCode(joinCode)
    },
    onSuccess() {
      toast.success('Join successfully!.')
      navigate('/menu', { replace: true })
      closeModal()
    },
    onError(error) {
      toast.error('An ERROR has occurred')
      console.error('Error during joining a table', error)
    },
  })

  const handleClose = () => {
    setOpciones('MENU')
    closeModal()
  }
  return (
    <>
      <Dialog
        open={activeModal == 'JOIN_TABLE'}
        onOpenChange={(isOpen) => {
          if (!isOpen) return handleClose()
        }}
      >
        <DialogContent className="sm:max-w-md rounded-3xl p-8">
          {opciones == 'MENU' && (
            <>
              <DialogHeader className="mb-2">
                <DialogTitle>Entrar a una mesa.</DialogTitle>
                <DialogDescription className="text-zinc-500 text-sm mt-1">
                  Seleccione una opcion para entrar a la mesa.
                </DialogDescription>
              </DialogHeader>
              <div className="flex justify-center items-center gap-4 flex-col">
                <div
                  className="flex justify-center flex-row w-full border shadow-sm gap-5 p-8 rounded-4xl 
          hover:border-[#8c1717] transition-colors cursor-pointer md:hidden sm:hidden"
                  onClick={() => {
                    setOpciones('QR')
                  }}
                >
                  <div
                    className="bg-[#8c1717] border shadow-sm rounded-full h-15 w-15 p-4 text-[#f37474] 
            flex items-center justify-center"
                  >
                    <QrCode />
                  </div>
                  <div>
                    <h2 className="font-bold text-md">Escanear codigo QR.</h2>
                    <p className="text-zinc-500 text-sm mt-1">
                      Usa tu camara para escanear el codigo de la mesa.
                    </p>
                  </div>
                </div>

                <div
                  className="flex justify-center flex-row w-full border shadow-sm gap-5 p-8 rounded-4xl 
          hover:border-[#8c1717] transition-colors cursor-pointer"
                  onClick={() => {
                    setOpciones('CODE')
                  }}
                >
                  <div
                    className="bg-[#8c1717] border shadow-sm rounded-full h-15 w-15 p-4 text-[#f37474] 
            flex items-center justify-center"
                  >
                    <Keyboard />
                  </div>
                  <div>
                    <h2 className="font-bold text-md">Ingresar codigo.</h2>
                    <p className="text-zinc-500 text-sm mt-1">
                      Escriba el codigo de 5 digitos de su mesa.
                    </p>
                  </div>
                </div>
              </div>
            </>
          )}
          {opciones == 'CODE' && (
            <>
              <DialogHeader className="">
                <DialogTitle>Ingresa el codigo.</DialogTitle>
                <DialogDescription className="text-zinc-500 text-sm mt-1">
                  Ingrese con cuidado el codigo para ingresar a la mesa.
                </DialogDescription>
              </DialogHeader>
              <div className="flex flex-col items-center justify-center w-full gap-5 ">
                <OtpInput
                  length={5}
                  value={joinCode}
                  onChange={(val) => setJoinCode(val)}
                />
                <Button
                  variant={'outline'}
                  className="w-full h-12 font-bold text-lg"
                  onClick={() => {
                    setJoinCode('')
                    setOpciones('MENU')
                  }}
                >
                  Volver
                </Button>
                <Button
                  className="w-full h-12 hover:bg-[#6a1111] font-bold text-lg"
                  onClick={() => {
                    mutation.mutate(joinCode)
                  }}
                  disabled={joinCode.length !== 5 || mutation.isPending}
                >
                  {mutation.isPending ? 'Entrando' : 'Confirmar'}
                </Button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
