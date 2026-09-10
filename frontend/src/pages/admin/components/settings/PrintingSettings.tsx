import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Ban, KeyRound, Printer, RotateCcw, Ticket, Trash2 } from 'lucide-react'
import { printingService, type PrintAgentResponse } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { CreateAgentModal } from './printing/CreateAgentModal'
import { AddPrinterModal } from './printing/AddPrinterModal'
import { GlobalDeleteModal } from '@/components/GlobalDeleteModal'

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
  const openModal = useUIStore((state) => state.openModal)

  const { data: allPrinters = [] } = useQuery({
    queryKey: ['printerConfigs', agent.id],
    queryFn: () => printingService.listPrinters(agent.id as string),
    enabled: !!agent.id,
  })
  // A "removed" printer is deactivated (active=false), not hard-deleted, so filter it out here.
  const printers = allPrinters.filter((printer) => printer.active)

  if (printers.length === 0) {
    return <p className="pl-3 text-sm text-zinc-400">{t('printingNoPrintersMessage')}</p>
  }

  return (
    <div className="space-y-1 pl-3">
      {printers.map((printer) => (
        <div key={printer.id} className="flex items-center justify-between gap-2 text-sm text-zinc-600">
          <span>
            {printer.role === 'KITCHEN' ? t('printingRoleKitchen') : t('printingRoleReceipt')} ·{' '}
            {printer.label} · {printerConnectionDetail(printer)}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-zinc-400 hover:text-red-600"
            aria-label={t('printingRemovePrinterAria')}
            onClick={() => openModal('DELETE_PRINTER', printer.id)}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
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

const NewPairingCodeButton = ({ agentId }: { agentId: string }) => {
  const { t } = useTranslation('admin')
  const [result, setResult] = useState<{ code: string; expiresAt?: string } | null>(null)

  const mutation = useMutation({
    mutationFn: () => printingService.createPairingCode(agentId),
    onSuccess: (created) => setResult({ code: created.code ?? '', expiresAt: created.expiresAt }),
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
        <Ticket className="mr-2 h-4 w-4" />
        {t('printingNewPairCodeButton')}
      </Button>
      <Dialog open={!!result} onOpenChange={(isOpen) => !isOpen && setResult(null)}>
        <DialogContent className="sm:max-w-md rounded-3xl p-6">
          <DialogHeader className="mb-4">
            <DialogTitle className="text-2xl font-bold text-zinc-800">{t('printingPairCodeTitle')}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-zinc-500">{t('printingPairCodeHint')}</p>
          <div className="flex items-center gap-2">
            <code className="flex-1 break-all rounded-xl bg-zinc-100 p-3 text-center text-2xl font-bold tracking-widest">
              {result?.code}
            </code>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                if (result?.code) {
                  navigator.clipboard?.writeText(result.code)
                  toast.success(t('printingCopyButton'))
                }
              }}
            >
              {t('printingCopyButton')}
            </Button>
          </div>
          {result?.expiresAt && (
            <p className="text-xs text-zinc-500">
              {t('printingPairCodeExpiresLabel')}: {new Date(result.expiresAt).toLocaleString()}
            </p>
          )}
          <DialogFooter>
            <Button type="button" onClick={() => setResult(null)}>
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

  const { data: allAgents = [] } = useQuery({
    queryKey: ['printAgents'],
    queryFn: () => printingService.listAgents(),
  })
  // "Eliminar" revokes an agent (status REVOKED) rather than dropping the row — hide the revoked
  // ones so the list reads as a delete.
  const agents = allAgents.filter((agent) => agent.status !== 'REVOKED')

  const { data: jobs = [] } = useQuery({
    queryKey: ['printJobs'],
    queryFn: () => printingService.listJobs(),
  })

  const retryMutation = useMutation({
    mutationFn: (jobId: string) => printingService.retryJob(jobId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['printJobs'] }),
  })

  const cancelMutation = useMutation({
    mutationFn: (jobId: string) => printingService.cancelJob(jobId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['printJobs'] }),
  })

  const cancelPendingMutation = useMutation({
    mutationFn: () => printingService.cancelPendingJobs(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['printJobs'] }),
  })

  const hasPendingJobs = jobs.some((job) => job.status === 'PENDING')

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
          <div className="flex items-center gap-3">
            <a
              href={
                import.meta.env.VITE_AGENT_DOWNLOAD_URL ??
                'https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe'
              }
              className="text-sm text-[#7a1315] underline"
            >
              {t('printingDownloadAgentLink')}
            </a>
            <Button onClick={() => openModal('CREATE_PRINT_AGENT')} className="rounded-xl">
              {t('printingGenerateAgentButton')}
            </Button>
          </div>
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
                    {agent.connected ? t('printingConnectedStatus') : t('printingDisconnectedStatus')} ·{' '}
                    <span className={agent.paired ? 'text-emerald-600' : 'text-amber-600'}>
                      {agent.paired ? t('printingPairedBadge') : t('printingUnpairedBadge')}
                    </span>
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {agent.id && <NewPairingCodeButton agentId={agent.id} />}
                  {agent.id && <RegenerateKeyButton agentId={agent.id} />}
                  <Button
                    variant="outline"
                    size="sm"
                    className="rounded-xl"
                    onClick={() =>
                      openModal('ADD_PRINTER', {
                        agentId: agent.id,
                        discoveredPrinters: agent.discoveredPrinters ?? [],
                      })
                    }
                  >
                    <Printer className="mr-2 h-4 w-4" />
                    {t('printingAddPrinterButton')}
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-zinc-400 hover:text-red-600"
                    aria-label={t('printingDeleteAgentAria')}
                    onClick={() => openModal('DELETE_PRINT_AGENT', agent.id)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
              <AgentPrinterList agent={agent} />
            </div>
          ))}
        </CardContent>
      </Card>

      <Card className="rounded-2xl border-zinc-200">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>{t('printingJobsTitle')}</CardTitle>
          {hasPendingJobs && (
            <Button
              variant="outline"
              size="sm"
              className="rounded-xl"
              onClick={() => cancelPendingMutation.mutate()}
              disabled={cancelPendingMutation.isPending}
            >
              <Ban className="mr-2 h-4 w-4" />
              {t('printingClearPendingButton')}
            </Button>
          )}
        </CardHeader>
        <CardContent className="space-y-2">
          {jobs.map((job) => {
            const canceled = job.status === 'CANCELED'
            const cancelable = job.status === 'PENDING' || job.status === 'ERROR'
            return (
              <div
                key={job.id}
                className="flex items-center justify-between rounded-xl border border-zinc-200 p-3"
              >
                <div>
                  <p className={`text-sm ${canceled ? 'text-zinc-400' : 'text-zinc-800'}`}>
                    {job.role} · {canceled ? t('printingJobCanceledStatus') : job.status}
                  </p>
                  {job.lastError && !canceled && (
                    <p className="text-sm text-red-600">{job.lastError}</p>
                  )}
                </div>
                {cancelable && job.id && (
                  <div className="flex items-center gap-2">
                    {job.status === 'ERROR' && (
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
                    <Button
                      variant="ghost"
                      size="sm"
                      className="rounded-xl text-zinc-500 hover:text-red-600"
                      onClick={() => cancelMutation.mutate(job.id!)}
                      disabled={cancelMutation.isPending}
                    >
                      <Ban className="mr-2 h-4 w-4" />
                      {t('printingCancelJobButton')}
                    </Button>
                  </div>
                )}
              </div>
            )
          })}
        </CardContent>
      </Card>

      <CreateAgentModal />
      <AddPrinterModal />
      <GlobalDeleteModal />
    </div>
  )
}
