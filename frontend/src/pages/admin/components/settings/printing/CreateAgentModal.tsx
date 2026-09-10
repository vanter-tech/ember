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
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { printingService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

const createAgentSchema = z.object({
  name: z.string().min(2).max(100),
})

type CreateAgentInputs = z.infer<typeof createAgentSchema>

export const CreateAgentModal = () => {
  const { t } = useTranslation('admin')
  const { activeModal, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [apiKey, setApiKey] = useState<string | null>(null)
  const [pairCode, setPairCode] = useState<string | null>(null)

  const form = useForm<CreateAgentInputs>({
    resolver: zodResolver(createAgentSchema),
    defaultValues: { name: '' },
  })

  const mutation = useMutation({
    mutationFn: (data: CreateAgentInputs) => printingService.createAgent(data.name),
    onSuccess: async (created) => {
      queryClient.invalidateQueries({ queryKey: ['printAgents'] })
      setApiKey(created.apiKey ?? null)
      if (created.id) {
        try {
          const { code } = await printingService.createPairingCode(created.id)
          setPairCode(code ?? null)
        } catch {
          setPairCode(null)
        }
      }
    },
    onError: () => {
      toast.error(t('printingAddButton'))
    },
  })

  const handleClose = () => {
    form.reset()
    setApiKey(null)
    setPairCode(null)
    closeModal()
  }

  const copy = (value: string) => {
    navigator.clipboard?.writeText(value)
    toast.success(t('printingCopyButton'))
  }

  return (
    <Dialog open={activeModal === 'CREATE_PRINT_AGENT'} onOpenChange={(isOpen) => !isOpen && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        {!apiKey ? (
          <>
            <DialogHeader className="mb-4">
              <DialogTitle className="text-2xl font-bold text-zinc-800">
                {t('printingNewAgentTitle')}
              </DialogTitle>
            </DialogHeader>
            <Form {...form}>
              <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
                <FormField
                  control={form.control}
                  name="name"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('nameLabel')}</FormLabel>
                      <FormControl>
                        <Input placeholder={t('printingAgentNamePlaceholder')} className="rounded-xl" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <DialogFooter>
                  <Button type="button" variant="outline" onClick={handleClose} disabled={mutation.isPending}>
                    {t('cancelButton')}
                  </Button>
                  <Button type="submit" disabled={mutation.isPending}>
                    {mutation.isPending ? t('savingEllipsisLabel') : t('printingCreateButton')}
                  </Button>
                </DialogFooter>
              </form>
            </Form>
          </>
        ) : (
          <>
            <DialogHeader className="mb-4">
              <DialogTitle className="text-2xl font-bold text-zinc-800">{t('printingPairCodeTitle')}</DialogTitle>
            </DialogHeader>
            {pairCode ? (
              <div className="flex items-center gap-2">
                <code className="flex-1 break-all rounded-xl bg-zinc-100 p-3 text-center text-2xl font-bold tracking-widest">
                  {pairCode}
                </code>
                <Button type="button" variant="outline" size="sm" onClick={() => copy(pairCode)}>
                  {t('printingCopyButton')}
                </Button>
              </div>
            ) : (
              <p className="text-sm text-zinc-500">{t('printingApiKeyWarning')}</p>
            )}
            <p className="text-xs text-zinc-500">{t('printingPairCodeHint')}</p>
            <details className="mt-3">
              <summary className="cursor-pointer text-xs text-zinc-500">{t('printingAdvancedInstallLabel')}</summary>
              <code className="mt-2 block break-all rounded-xl bg-zinc-100 p-3 text-xs">{apiKey}</code>
            </details>
            <DialogFooter>
              <Button type="button" onClick={handleClose}>
                {t('printingCloseButton')}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
