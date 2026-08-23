import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import { useQuery } from '@tanstack/react-query'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { inventoryService, inventoryMenuItemService, type InventoryItemRequest, type InventoryItemResponse } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

export const NewInventoryItemModal = () => {
  const { activeModal, closeModal } = useUIStore()
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const { data: menuItems = [] } = useQuery({
    queryKey: ['menuItemsForInventory'],
    queryFn: inventoryMenuItemService.listAll,
    enabled: activeModal === 'CREATE_INVENTORY_ITEM',
  })
  const { data: trackedItems = [] } = useQuery({
    queryKey: ['inventoryItems'],
    queryFn: inventoryService.getAll,
    enabled: activeModal === 'CREATE_INVENTORY_ITEM',
  })
  const trackedMenuItemIds = new Set((trackedItems as InventoryItemResponse[]).map((i) => i.menuItemId))
  const untrackedMenuItems = menuItems.filter((item) => item.id !== undefined && !trackedMenuItemIds.has(item.id))

  const schema = z.object({
    menuItemId: z.number(),
    unit: z.string().min(1, t('inventoryUnitLabel')),
    currentStock: z.number().min(0),
    lowStockThreshold: z.number().min(0),
  })
  type FormInputs = z.infer<typeof schema>

  const form = useForm<FormInputs>({
    resolver: zodResolver(schema),
    defaultValues: { menuItemId: undefined, unit: '', currentStock: 0, lowStockThreshold: 0 },
  })

  const mutation = useMutation({
    mutationFn: (request: InventoryItemRequest) => inventoryService.create(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inventoryItems'] })
      toast.success(t('inventoryCreatedToast'))
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error(t('genericErrorToast'))
    },
  })

  const onSubmit = (data: FormInputs) => {
    mutation.mutate({
      menuItemId: data.menuItemId,
      unit: data.unit,
      currentStock: data.currentStock,
      lowStockThreshold: data.lowStockThreshold,
    })
  }

  return (
    <Dialog open={activeModal == 'CREATE_INVENTORY_ITEM'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('newInventoryItemDialogTitle')}
          </DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
            <FormField
              control={form.control}
              name="menuItemId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('inventoryMenuItemLabel')}</FormLabel>
                  <Select
                    value={field.value ? String(field.value) : undefined}
                    onValueChange={(value) => field.onChange(Number(value))}
                  >
                    <FormControl>
                      <SelectTrigger className="rounded-xl">
                        <SelectValue placeholder={t('inventoryMenuItemPlaceholder')} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {untrackedMenuItems.length === 0 && (
                        <div className="px-3 py-2 text-sm text-zinc-500">{t('inventoryNoUntrackedItems')}</div>
                      )}
                      {untrackedMenuItems.map((item) => (
                        <SelectItem key={item.id} value={String(item.id)}>
                          {item.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="unit"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('inventoryUnitLabel')}</FormLabel>
                  <FormControl>
                    <Input placeholder={t('inventoryUnitPlaceholder')} className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="currentStock"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('inventoryInitialStockLabel')}</FormLabel>
                    <FormControl>
                      <Input type="number" step="0.001" className="rounded-xl" {...field}
                        onChange={(e) => field.onChange(Number(e.target.value))} />
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
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                {t('cancelButton')}
              </Button>
              <Button type="submit" disabled={mutation.isPending || untrackedMenuItems.length === 0}>
                {mutation.isPending ? t('savingButton') : t('saveButton')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
