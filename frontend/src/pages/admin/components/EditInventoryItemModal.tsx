import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
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
import { inventoryService, type InventoryItemUpdateRequest, type InventoryItemResponse } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

export const EditInventoryItemModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const item = modalPayload as InventoryItemResponse | undefined
  const [restockAmount, setRestockAmount] = useState(0)

  const schema = z.object({
    unit: z.string().min(1),
    lowStockThreshold: z.number().min(0),
  })
  type FormInputs = z.infer<typeof schema>

  const form = useForm<FormInputs>({
    resolver: zodResolver(schema),
    defaultValues: { unit: '', lowStockThreshold: 0 },
    values: item && {
      unit: item.unit ?? '',
      lowStockThreshold: item.lowStockThreshold ?? 0,
    },
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventoryItems'] })

  const updateMutation = useMutation({
    mutationFn: (request: InventoryItemUpdateRequest) => inventoryService.update(item!.id!, request),
    onSuccess: () => {
      invalidate()
      toast.success(t('inventoryUpdatedToast'))
      closeModal()
    },
    onError: () => toast.error(t('genericErrorToast')),
  })

  const restockMutation = useMutation({
    mutationFn: (delta: number) => inventoryService.restock(item!.id!, delta),
    onSuccess: () => {
      invalidate()
      toast.success(t('inventoryRestockedToast'))
      setRestockAmount(0)
    },
    onError: () => toast.error(t('genericErrorToast')),
  })

  const removeMutation = useMutation({
    mutationFn: () => inventoryService.remove(item!.id!),
    onSuccess: () => {
      invalidate()
      toast.success(t('inventoryRemovedToast'))
      closeModal()
    },
    onError: () => toast.error(t('genericErrorToast')),
  })

  if (!item) return null

  const onSubmit = (data: FormInputs) => {
    updateMutation.mutate({ unit: data.unit, lowStockThreshold: data.lowStockThreshold })
  }

  const isPending = updateMutation.isPending || restockMutation.isPending || removeMutation.isPending

  return (
    <Dialog open={activeModal == 'EDIT_INVENTORY_ITEM'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('editInventoryItemDialogTitle')}
          </DialogTitle>
        </DialogHeader>

        <div className="mb-5 flex items-center justify-between gap-3 rounded-lg border p-4">
          <div>
            <p className="text-sm text-zinc-500">{t('inventoryStockLabel')}</p>
            <p className="text-lg font-semibold">{item.currentStock} {item.unit}</p>
          </div>
          <div className="flex items-center gap-2">
            <Input
              type="number"
              step="0.001"
              className="w-28 rounded-xl"
              placeholder={t('inventoryRestockPlaceholder')}
              value={restockAmount || ''}
              onChange={(e) => setRestockAmount(Number(e.target.value))}
            />
            <Button
              type="button"
              variant="outline"
              disabled={restockMutation.isPending || restockAmount === 0}
              onClick={() => restockMutation.mutate(restockAmount)}
            >
              {t('inventoryRestockButton')}
            </Button>
          </div>
        </div>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
            <FormField
              control={form.control}
              name="unit"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('inventoryUnitLabel')}</FormLabel>
                  <FormControl>
                    <Input className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="lowStockThreshold"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('inventoryThresholdLabel')}</FormLabel>
                  <FormControl>
                    <Input type="number" step="0.001" className="rounded-xl" {...field}
                      onChange={(e) => field.onChange(Number(e.target.value))} />
                  </FormControl>
                </FormItem>
              )}
            />

            <DialogFooter className="justify-between">
              <Button
                type="button"
                variant="ghost"
                className="text-destructive"
                disabled={isPending}
                onClick={() => removeMutation.mutate()}
              >
                {t('inventoryRemoveTrackingButton')}
              </Button>
              <div className="flex gap-2">
                <Button type="button" variant="outline" onClick={closeModal} disabled={isPending}>
                  {t('cancelButton')}
                </Button>
                <Button type="submit" disabled={isPending}>
                  {updateMutation.isPending ? t('savingButton') : t('saveButton')}
                </Button>
              </div>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
