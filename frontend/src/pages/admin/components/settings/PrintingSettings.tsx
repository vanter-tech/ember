import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Printer, RotateCcw } from 'lucide-react'
import { printingService, type PrintAgentResponse } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { CreateAgentModal } from './printing/CreateAgentModal'
import { AddPrinterModal } from './printing/AddPrinterModal'

const printerConnectionDetail = (printer: {
  connectionType?: string
  host?: string
  port?: number
  comPort?: string
  windowsQueueName?: string
  renderMode?: string
}) => {
  switch (printer.connectionType) {
    case 'NETWORK':
      return `${printer.host ?? ''}:${printer.port ?? ''}`
    case 'USB':
      return printer.comPort ?? ''
    case 'WINDOWS_QUEUE':
      return printer.renderMode === 'DRIVER'
        ? `${printer.windowsQueueName ?? ''} (driver)`
        : (printer.windowsQueueName ?? '')
    default:
      return ''
  }
}

const AgentPrinterList = ({ agent }: { agent: PrintAgentResponse }) => {
  const { t } = useTranslation('admin')

  const { data: printers = [] } = useQuery({
    queryKey: ['printerConfigs', agent.id],
    queryFn: () => printingService.listPrinters(agent.id as string),
    enabled: !!agent.id,
  })

  if (printers.length === 0) {
    return <p className="pl-3 text-sm text-zinc-400">{t('printingNoPrintersMessage')}</p>
  }

  return (
    <div className="space-y-1 pl-3">
      {printers.map((printer) => (
        <div key={printer.id} className="flex items-center justify-between text-sm text-zinc-600">
          <span>
            {printer.role === 'KITCHEN' ? t('printingRoleKitchen') : t('printingRoleReceipt')} ·{' '}
            {printer.label} · {printerConnectionDetail(printer)}
          </span>
          <span className={printer.active ? 'text-emerald-600' : 'text-zinc-400'}>
            {printer.active ? t('printingPrinterActiveStatus') : t('printingPrinterInactiveStatus')}
          </span>
        </div>
      ))}
    </div>
  )
}

const RegenerateKeyButton = ({ agentId }: { agentId: string }) => {
  const { t } = useTranslation('admin')
  const [newKey, setNewKey] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => printingService.regenerateKey(agentId),
    onSuccess: (created) => setNewKey(created.apiKey ?? null),
  })

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        className="rounded-xl"
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
      >
        <KeyRound className="mr-2 h-4 w-4" />
        {t('printingRegenerateKeyButton')}
      </Button>
      <Dialog open={!!newKey} onOpenChange={(isOpen) => !isOpen && setNewKey(null)}>
        <DialogContent className="sm:max-w-md rounded-3xl p-6">
          <DialogHeader className="mb-4">
            <DialogTitle className="text-2xl font-bold text-zinc-800">{t('printingApiKeyTitle')}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-zinc-500">{t('printingApiKeyWarning')}</p>
          <code className="block break-all rounded-xl bg-zinc-100 p-3 text-sm">{newKey}</code>
          <DialogFooter>
            <Button type="button" onClick={() => setNewKey(null)}>
              {t('printingCloseButton')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

export const PrintingSettings = () => {
  const { t } = useTranslation('admin')
  const openModal = useUIStore((state) => state.openModal)
  const queryClient = useQueryClient()

  const { data: agents = [] } = useQuery({
    queryKey: ['printAgents'],
    queryFn: () => printingService.listAgents(),
  })

  const { data: jobs = [] } = useQuery({
    queryKey: ['printJobs'],
    queryFn: () => printingService.listJobs(),
  })

  const retryMutation = useMutation({
    mutationFn: (jobId: string) => printingService.retryJob(jobId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['printJobs'] }),
  })

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-4">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center shrink-0">
          <Printer className="w-6 h-6" />
        </div>
        <div>
          <h2 className="text-xl font-semibold text-zinc-800">{t('printingLabel')}</h2>
          <p className="text-sm text-muted-foreground">{t('printingPageDescription')}</p>
        </div>
      </div>

      <Card className="rounded-2xl border-zinc-200">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>{t('printingAgentsTitle')}</CardTitle>
          <Button onClick={() => openModal('CREATE_PRINT_AGENT')} className="rounded-xl">
            {t('printingGenerateAgentButton')}
          </Button>
        </CardHeader>
        <CardContent className="space-y-2">
          {agents.length === 0 && (
            <p className="text-sm text-zinc-500">{t('printingNoAgentsMessage')}</p>
          )}
          {agents.map((agent) => (
            <div key={agent.id} className="space-y-2 rounded-xl border border-zinc-200 p-3">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-zinc-800">{agent.name}</p>
                  <p className="text-sm text-zinc-500">
                    {agent.status} ·{' '}
                    {agent.connected ? t('printingConnectedStatus') : t('printingDisconnectedStatus')}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {agent.id && <RegenerateKeyButton agentId={agent.id} />}
                  <Button
                    variant="outline"
                    size="sm"
                    className="rounded-xl"
                    onClick={() => openModal('ADD_PRINTER', agent.id)}
                  >
                    <Printer className="mr-2 h-4 w-4" />
                    {t('printingAddPrinterButton')}
                  </Button>
                </div>
              </div>
              <AgentPrinterList agent={agent} />
            </div>
          ))}
        </CardContent>
      </Card>

      <Card className="rounded-2xl border-zinc-200">
        <CardHeader>
          <CardTitle>{t('printingJobsTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {jobs.map((job) => (
            <div
              key={job.id}
              className="flex items-center justify-between rounded-xl border border-zinc-200 p-3"
            >
              <div>
                <p className="text-sm text-zinc-800">
                  {job.role} · {job.status}
                </p>
                {job.lastError && <p className="text-sm text-red-600">{job.lastError}</p>}
              </div>
              {job.status === 'ERROR' && job.id && (
                <Button
                  variant="outline"
                  size="sm"
                  className="rounded-xl"
                  onClick={() => retryMutation.mutate(job.id!)}
                  disabled={retryMutation.isPending}
                >
                  <RotateCcw className="mr-2 h-4 w-4" />
                  {t('printingRetryButton')}
                </Button>
              )}
            </div>
          ))}
        </CardContent>
      </Card>

      <CreateAgentModal />
      <AddPrinterModal />
    </div>
  )
}
