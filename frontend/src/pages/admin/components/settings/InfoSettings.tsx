import { Info } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useTranslation } from '@/lib/i18n'

const VANTER_URL = 'https://vanter.net'
const SUPPORT_URL = 'https://ember.vanter.net/contacto'

export const InfoSettings = () => {
  const { t } = useTranslation('admin')
  const year = new Date().getFullYear()

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <Info className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('infoCardTitle')}</CardTitle>
          <CardDescription>{t('infoCardDescription')}</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-4 text-sm text-zinc-700">
          <p>
            {t('infoMadeByPrefix')}{' '}
            <a
              href={VANTER_URL}
              target="_blank"
              rel="noreferrer"
              className="font-medium text-[#7a1315] hover:underline"
            >
              vanter.net
            </a>
          </p>
          <p className="text-zinc-500">{t('infoLocation')}</p>
          <p className="text-xs text-muted-foreground">{t('infoCopyright', { year })}</p>
          <p className="pt-2 border-t text-zinc-500">
            {t('infoSupportPrefix')}{' '}
            <a
              href={SUPPORT_URL}
              target="_blank"
              rel="noreferrer"
              className="font-medium text-[#7a1315] hover:underline"
            >
              {t('infoSupportLinkText')}
            </a>
          </p>
        </div>
      </CardContent>
    </Card>
  )
}
