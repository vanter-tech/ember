import { useTranslation } from '@/lib/i18n'

const VANTER_URL = 'https://vanter.net'
const SUPPORT_URL = 'https://ember.vanter.net/contacto'

export const InfoSettings = () => {
  const { t } = useTranslation('admin')
  const year = new Date().getFullYear()

  return (
    <div className="h-full flex flex-col justify-center gap-8 rounded-xl border border-zinc-100 bg-card p-6 shadow-sm ring-1 ring-foreground/10 md:p-10">
      {/* Settings.tsx's outer container no longer draws its own frame (every tab owns one now),
          so this tab needs its own — same visual weight as every other tab's <Card>. Vanter
          (builds & distributes) on the left, a hair-line divider, Ember (the product) on the
          right. */}
      <div className="flex items-center justify-center gap-6 sm:gap-10">
        <a
          href={VANTER_URL}
          target="_blank"
          rel="noreferrer"
          className="shrink-0 transition-opacity hover:opacity-80"
        >
          <img
            src={`${import.meta.env.BASE_URL}vanter-tech_logo.svg`}
            alt="Vanter"
            className="h-20 w-auto object-contain sm:h-24"
          />
        </a>
        <span aria-hidden className="h-16 w-px bg-zinc-200 sm:h-20" />
        <img
          src={`${import.meta.env.BASE_URL}ember_logo_info.svg`}
          alt="Ember"
          className="h-20 w-auto object-contain sm:h-24"
        />
      </div>

      <div className="mx-auto h-px w-full max-w-xs bg-zinc-200" />

      <div className="space-y-1.5 text-center text-sm text-zinc-600">
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
        <p className="pt-3">
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
    </div>
  )
}
