import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { useTranslation } from '@/lib/i18n'

const openShiftSchema = z.object({
  openingFloat: z.coerce.number().min(0, 'El fondo inicial no puede ser negativo'),
})

type OpenShiftInputs = z.infer<typeof openShiftSchema>

export const OpenShiftDialog = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, closeModal } = useUIStore()
  const queryClient = useQueryClient()

  const form = useForm({
    resolver: zodResolver(openShiftSchema),
    defaultValues: { openingFloat: 0 },
  })

  const mutation = useMutation({
    mutationFn: (data: OpenShiftInputs) => cashShiftService.open(data.openingFloat),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      toast.success('Caja abierta correctamente.')
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo abrir la caja.')
    },
  })

  return (
    <Dialog open={activeModal === 'OPEN_SHIFT'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('openShiftTitle')}</DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
            <FormField
              control={form.control}
              name="openingFloat"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('openingFloatLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0"
                      className="rounded-xl"
                      {...field}
                      value={field.value as number}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                {t('cancelButton')}
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? t('openingLabel') : t('openCajaButton')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
