import { useEffect, useMemo, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImagePlus, Loader2, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { ticketLogoService } from '@/lib/api'
import { extractPlanGateError } from '@/lib/planGate'
import { useTranslation } from '@/lib/i18n'

const MAX_BYTES = 2 * 1024 * 1024

/** Receipt logo: upload / replace / remove. Independent of the Ticket form's Save button. */
export const TicketLogoField = () => {
  const { t } = useTranslation('admin')
  const { t: tCommon } = useTranslation('common')
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)

  const { data: logo, isPending } = useQuery({
    queryKey: ['ticketLogo'],
    queryFn: () => ticketLogoService.get(),
  })

  const previewUrl = useMemo(() => (logo ? URL.createObjectURL(logo) : null), [logo])
  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl)
    },
    [previewUrl],
  )

  const upload = useMutation({
    mutationFn: (file: File) => ticketLogoService.upload(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticketLogo'] })
      toast.success(t('ticketLogoUploadedToast'))
    },
    onError: (error) => {
      const gate = extractPlanGateError(error)
      toast.error(
        gate
          ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
          : t('ticketLogoUploadErrorToast'),
      )
    },
  })

  const remove = useMutation({
    mutationFn: () => ticketLogoService.remove(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticketLogo'] })
      toast.success(t('ticketLogoRemovedToast'))
    },
    onError: () => toast.error(t('ticketLogoRemoveErrorToast')),
  })

  const onPick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (file.size > MAX_BYTES) {
      toast.error(t('ticketLogoUploadErrorToast'))
      return
    }
    upload.mutate(file)
  }

  const busy = upload.isPending || remove.isPending

  return (
    <div className="space-y-2">
      <Label>{t('ticketLogoLabel')}</Label>
      <p className="text-xs text-muted-foreground">{t('ticketLogoDescription')}</p>
      <div className="flex items-center gap-4 rounded-xl border border-zinc-200 p-4">
        <div className="flex h-24 w-40 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-dashed border-zinc-300 bg-white">
          {isPending ? (
            <Loader2 className="h-5 w-5 animate-spin text-zinc-400" />
          ) : previewUrl ? (
            <img src={previewUrl} alt={t('ticketLogoPreviewAlt')} className="max-h-full max-w-full object-contain" />
          ) : (
            <span className="text-xs text-zinc-400">{t('ticketLogoEmpty')}</span>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <input
            ref={inputRef}
            type="file"
            accept="image/png,image/jpeg,image/gif"
            className="hidden"
            data-testid="ticket-logo-input"
            onChange={onPick}
          />
          <Button type="button" variant="outline" disabled={busy} onClick={() => inputRef.current?.click()}>
            {upload.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <ImagePlus className="mr-2 h-4 w-4" />
            )}
            {previewUrl ? t('ticketLogoReplaceButton') : t('ticketLogoUploadButton')}
          </Button>
          {previewUrl && (
            <Button
              type="button"
              variant="ghost"
              disabled={busy}
              className="text-[#8c1717]"
              onClick={() => remove.mutate()}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              {t('ticketLogoRemoveButton')}
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
