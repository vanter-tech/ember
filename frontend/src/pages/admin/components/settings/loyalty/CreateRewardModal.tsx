import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { loyaltyRewardService } from '@/lib/api'
import { TIER_LABELS } from './types'

const createRewardSchema = z.object({
  name: z.string().min(2, 'El nombre debe tener al menos 2 caracteres').max(255),
  description: z.string().max(1000).optional(),
  requiredTier: z.enum(['BRONCE', 'PLATA', 'ORO', 'PLATINO']),
})

type CreateRewardInputs = z.infer<typeof createRewardSchema>

export const CreateRewardModal = () => {
  const { activeModal, closeModal } = useUIStore()
  const queryClient = useQueryClient()

  const form = useForm<CreateRewardInputs>({
    resolver: zodResolver(createRewardSchema),
    defaultValues: { name: '', description: '', requiredTier: 'BRONCE' },
  })

  const mutation = useMutation({
    mutationFn: loyaltyRewardService.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loyaltyRewards'] })
      toast.success('Recompensa creada.')
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo crear la recompensa.')
    },
  })

  const handleClose = () => {
    form.reset()
    closeModal()
  }

  return (
    <Dialog
      open={activeModal === 'CREATE_REWARD'}
      onOpenChange={(isOpen) => !isOpen && handleClose()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            Nueva recompensa
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            Agrega un beneficio al catálogo de fidelización.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((data) => mutation.mutate(data))}
            className="space-y-5"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Nombre</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Ej. Postre gratis"
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Descripción</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Ej. Aplica en cualquier visita al alcanzar el nivel requerido"
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="requiredTier"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Nivel requerido</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {(Object.keys(TIER_LABELS) as Array<keyof typeof TIER_LABELS>).map((tier) => (
                        <SelectItem key={tier} value={tier}>
                          {TIER_LABELS[tier]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={handleClose}
                disabled={mutation.isPending}
              >
                Cancelar
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Guardando...' : 'Crear recompensa'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
