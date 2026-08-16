import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'

const movementSchema = z.object({
  type: z.enum(['CASH_IN', 'CASH_OUT']),
  amount: z.coerce.number().positive('El monto debe ser mayor a cero'),
  reason: z.string().min(3, 'Escribe un motivo'),
})

type MovementInputs = z.infer<typeof movementSchema>

export const MovementDialog = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const shiftId = modalPayload?.shiftId as number | undefined

  const form = useForm({
    resolver: zodResolver(movementSchema),
    defaultValues: { type: 'CASH_IN', amount: 0, reason: '' },
  })

  const mutation = useMutation({
    mutationFn: (data: MovementInputs) => cashShiftService.recordMovement(shiftId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftDetail', shiftId] })
      toast.success('Movimiento registrado.')
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo registrar el movimiento.')
    },
  })

  return (
    <Dialog open={activeModal === 'CASH_MOVEMENT'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">Movimiento manual</DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
            <FormField
              control={form.control}
              name="type"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tipo</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="CASH_IN">Entrada</SelectItem>
                      <SelectItem value="CASH_OUT">Salida</SelectItem>
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="amount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Monto</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0.01"
                      className="rounded-xl"
                      {...field}
                      value={field.value as number}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="reason"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Motivo</FormLabel>
                  <FormControl>
                    <Textarea className="resize-none h-20 rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                Cancelar
              </Button>
              <Button type="submit" disabled={mutation.isPending || !shiftId}>
                {mutation.isPending ? 'Guardando...' : 'Registrar'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
