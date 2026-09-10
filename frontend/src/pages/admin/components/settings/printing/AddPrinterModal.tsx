import { useState } from 'react'
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
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { printingService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

const addPrinterSchema = z.object({
  role: z.enum(['KITCHEN', 'RECEIPT']),
  connectionType: z.enum(['NETWORK', 'USB', 'WINDOWS_QUEUE']),
  host: z.string().optional(),
  port: z.string().optional(),
  comPort: z.string().optional(),
  windowsQueueName: z.string().optional(),
  renderMode: z.enum(['RAW', 'DRIVER']),
  label: z.string().min(1).max(100),
})

type AddPrinterInputs = z.infer<typeof addPrinterSchema>

export const AddPrinterModal = () => {
  const { t } = useTranslation('admin')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const { agentId, discoveredPrinters = [] } = (modalPayload ?? {}) as {
    agentId?: string
    discoveredPrinters?: Array<{ name?: string; inkjetGuess?: boolean }>
  }
  const knownQueues = discoveredPrinters.filter((p): p is { name: string; inkjetGuess?: boolean } => !!p.name)
  const [queueFreeText, setQueueFreeText] = useState(false)
  const queryClient = useQueryClient()

  const form = useForm<AddPrinterInputs>({
    resolver: zodResolver(addPrinterSchema),
    defaultValues: {
      role: 'KITCHEN',
      connectionType: 'NETWORK',
      host: '',
      port: '9100',
      comPort: '',
      windowsQueueName: '',
      renderMode: 'RAW',
      label: '',
    },
  })

  const connectionType = form.watch('connectionType')

  const mutation = useMutation({
    mutationFn: (data: AddPrinterInputs) =>
      printingService.addPrinter(agentId as string, {
        role: data.role,
        connectionType: data.connectionType,
        label: data.label,
        host: data.connectionType === 'NETWORK' ? data.host : undefined,
        port: data.connectionType === 'NETWORK' ? Number(data.port) : undefined,
        comPort: data.connectionType === 'USB' ? data.comPort : undefined,
        windowsQueueName: data.connectionType === 'WINDOWS_QUEUE' ? data.windowsQueueName : undefined,
        renderMode: data.connectionType === 'WINDOWS_QUEUE' ? data.renderMode : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['printerConfigs', agentId] })
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error(t('printingAddButton'))
    },
  })

  const handleClose = () => {
    form.reset()
    setQueueFreeText(false)
    closeModal()
  }

  return (
    <Dialog open={activeModal === 'ADD_PRINTER'} onOpenChange={(isOpen) => !isOpen && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('printingAddPrinterTitle')}</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
            <FormField
              control={form.control}
              name="role"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('printingRoleKitchen')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="KITCHEN">{t('printingRoleKitchen')}</SelectItem>
                      <SelectItem value="RECEIPT">{t('printingRoleReceipt')}</SelectItem>
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="connectionType"
              render={({ field }) => (
                <FormItem>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="NETWORK">{t('printingConnectionNetwork')}</SelectItem>
                      <SelectItem value="USB">{t('printingConnectionUsb')}</SelectItem>
                      <SelectItem value="WINDOWS_QUEUE">{t('printingConnectionWindowsQueue')}</SelectItem>
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />
            {connectionType === 'NETWORK' ? (
              <>
                <FormField
                  control={form.control}
                  name="host"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <Input placeholder={t('printingHostPlaceholder')} className="rounded-xl" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="port"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <Input placeholder={t('printingPortPlaceholder')} className="rounded-xl" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </>
            ) : connectionType === 'USB' ? (
              <FormField
                control={form.control}
                name="comPort"
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <Input placeholder={t('printingComPortPlaceholder')} className="rounded-xl" {...field} />
                    </FormControl>
                  </FormItem>
                )}
              />
            ) : (
              <>
                <FormField
                  control={form.control}
                  name="windowsQueueName"
                  render={({ field }) => (
                    <FormItem>
                      {knownQueues.length > 0 && (
                        <Select
                          value={queueFreeText ? '__other__' : field.value || ''}
                          onValueChange={(value) => {
                            if (value === '__other__') {
                              setQueueFreeText(true)
                              field.onChange('')
                              return
                            }
                            setQueueFreeText(false)
                            field.onChange(value)
                            if (knownQueues.find((p) => p.name === value)?.inkjetGuess) {
                              form.setValue('renderMode', 'DRIVER')
                            }
                          }}
                        >
                          <FormControl>
                            <SelectTrigger className="w-full rounded-xl">
                              <SelectValue placeholder={t('printingQueueNamePlaceholder')} />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {knownQueues.map((p) => (
                              <SelectItem key={p.name} value={p.name}>
                                {p.name}
                              </SelectItem>
                            ))}
                            <SelectItem value="__other__">{t('printingQueueOtherOption')}</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                      {(knownQueues.length === 0 || queueFreeText) && (
                        <FormControl>
                          <Input
                            placeholder={t('printingQueueNamePlaceholder')}
                            className="rounded-xl"
                            {...field}
                          />
                        </FormControl>
                      )}
                      <p className="text-xs text-zinc-500">{t('printingQueueNameHint')}</p>
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="renderMode"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('printingRenderModeLabel')}</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <FormControl>
                          <SelectTrigger className="w-full rounded-xl">
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="RAW">{t('printingRenderModeRaw')}</SelectItem>
                          <SelectItem value="DRIVER">{t('printingRenderModeDriver')}</SelectItem>
                        </SelectContent>
                      </Select>
                    </FormItem>
                  )}
                />
              </>
            )}
            <FormField
              control={form.control}
              name="label"
              render={({ field }) => (
                <FormItem>
                  <FormControl>
                    <Input placeholder={t('printingPrinterLabelPlaceholder')} className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={handleClose} disabled={mutation.isPending}>
                {t('cancelButton')}
              </Button>
              <Button type="submit" disabled={mutation.isPending || !agentId}>
                {mutation.isPending ? t('savingEllipsisLabel') : t('printingAddButton')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
