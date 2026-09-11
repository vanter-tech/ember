import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Download } from 'lucide-react'
import { exportService } from '@/lib/api'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useTranslation } from '@/lib/i18n'

const toIsoStart = (date: string): string | undefined => (date ? `${date}T00:00:00` : undefined)
const toIsoEnd = (date: string): string | undefined => (date ? `${date}T23:59:59` : undefined)

export const ExportSettings = () => {
  const { t } = useTranslation('admin')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')

  const downloadMutation = useMutation({
    mutationFn: () => exportService.downloadTenantData(toIsoStart(fromDate), toIsoEnd(toDate)),
    onSuccess: (blob) => {
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `ember-export-${new Date().toISOString().slice(0, 10)}.zip`
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      toast.success(t('exportDownloadedToast'))
    },
    onError: () => toast.error(t('exportErrorToast')),
  })

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <Download className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('exportCardTitle')}</CardTitle>
          <CardDescription>{t('exportCardDescription')}</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-6">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="export-from">{t('exportFromLabel')}</Label>
              <Input
                id="export-from"
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="export-to">{t('exportToLabel')}</Label>
              <Input
                id="export-to"
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
              />
            </div>
          </div>
          <p className="text-xs text-muted-foreground">{t('exportHint')}</p>
          <Button onClick={() => downloadMutation.mutate()} disabled={downloadMutation.isPending}>
            <Download className="w-4 h-4 mr-2" />
            {downloadMutation.isPending ? t('exportDownloadingLabel') : t('exportDownloadButton')}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
