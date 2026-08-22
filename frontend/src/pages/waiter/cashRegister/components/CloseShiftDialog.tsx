import { useMemo, useState } from 'react'
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
import { cashShiftService, type CashShiftResponse } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'

const createCloseShiftSchema = (t: ReturnType<typeof useTranslation<'waiter'>>['t']) =>
  z.object({
    countedCash: z.coerce.number().min(0, t('countedCashNegativeError')),
  })

type CloseShiftInputs = z.infer<ReturnType<typeof createCloseShiftSchema>>

export const CloseShiftDialog = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const shiftId = modalPayload?.shiftId as number | undefined
  const [result, setResult] = useState<CashShiftResponse | null>(null)
  const closeShiftSchema = useMemo(() => createCloseShiftSchema(t), [t])

  const form = useForm({
    resolver: zodResolver(closeShiftSchema),
    defaultValues: { countedCash: 0 },
  })

  const mutation = useMutation({
    mutationFn: (data: CloseShiftInputs) => cashShiftService.close(shiftId!, data.countedCash),
    onSuccess: (closed) => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      setResult(closed)
    },
    onError: () => {
      toast.error(t('shiftCloseErrorToast'))
    },
  })

  const handleOpenChange = (isOpen: boolean) => {
    if (!isOpen) {
      form.reset()
      setResult(null)
      closeModal()
    }
  }

  return (
    <Dialog open={activeModal === 'CLOSE_SHIFT'} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('closeShiftTitle')}</DialogTitle>
        </DialogHeader>

        {!result ? (
          <Form {...form}>
            <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
              <p className="text-sm text-muted-foreground">
                {t('closeShiftDescription')}
              </p>
              <FormField
                control={form.control}
                name="countedCash"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('countedCashLabel')}</FormLabel>
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
                <Button type="submit" disabled={mutation.isPending || !shiftId}>
                  {mutation.isPending ? t('closingShiftLabel') : t('confirmCountButton')}
                </Button>
              </DialogFooter>
            </form>
          </Form>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-xs text-muted-foreground">{t('expectedLabel')}</p>
                <p className="text-lg font-bold">{formatCurrency(result.expectedCash ?? 0)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">{t('countedLabel')}</p>
                <p className="text-lg font-bold">{formatCurrency(result.countedCash ?? 0)}</p>
              </div>
              <div className="col-span-2">
                <p className="text-xs text-muted-foreground">{t('differenceLabel')}</p>
                <p
                  className={`text-lg font-bold ${
                    (result.variance ?? 0) === 0
                      ? 'text-primary'
                      : (result.variance ?? 0) > 0
                        ? 'text-emerald-600'
                        : 'text-destructive'
                  }`}
                >
                  {formatCurrency(result.variance ?? 0)}
                </p>
              </div>
            </div>
            <DialogFooter>
              <Button onClick={() => handleOpenChange(false)}>{t('closeButton')}</Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
