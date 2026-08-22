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
import { Switch } from '@/components/ui/switch'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { loyaltyRewardService, type LoyaltyRewardResponse } from '@/lib/api'
import { TIER_LABELS } from './types'
import { useTranslation } from '@/lib/i18n'

const editRewardSchema = z.object({
  name: z.string().min(2, 'El nombre debe tener al menos 2 caracteres').max(255),
  description: z.string().max(1000).optional(),
  requiredTier: z.enum(['BRONCE', 'PLATA', 'ORO', 'PLATINO']),
  active: z.boolean(),
})

type EditRewardInputs = z.infer<typeof editRewardSchema>

export const EditRewardModal = () => {
  const { t } = useTranslation('admin')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const reward = modalPayload as LoyaltyRewardResponse | null

  const form = useForm<EditRewardInputs>({
    resolver: zodResolver(editRewardSchema),
    values: {
      name: reward?.name ?? '',
      description: reward?.description ?? '',
      requiredTier: reward?.requiredTier ?? 'BRONCE',
      active: reward?.active ?? true,
    },
  })

  const mutation = useMutation({
    mutationFn: (data: EditRewardInputs) => loyaltyRewardService.update(reward!.id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loyaltyRewards'] })
      toast.success('Recompensa actualizada.')
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo actualizar la recompensa.')
    },
  })

  return (
    <Dialog
      open={activeModal === 'EDIT_REWARD'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('editRewardTitle')}
          </DialogTitle>
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
                  <FormLabel>{t('nameLabel')}</FormLabel>
                  <FormControl>
                    <Input className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('descriptionFieldLabel')}</FormLabel>
                  <FormControl>
                    <Textarea className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="requiredTier"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('requiredTierLabel')}</FormLabel>
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

            <FormField
              control={form.control}
              name="active"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between gap-3 rounded-lg border p-4">
                  <FormLabel>{t('activeRewardLabel')}</FormLabel>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={closeModal}
                disabled={mutation.isPending}
              >
                {t('cancelButton')}
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? t('savingEllipsisLabel') : t('saveChangesButton')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
