import { useTranslation } from '@/lib/i18n'

/** Footer brand + credit pinned to the bottom edge of a `relative` auth container. */
export const PoweredByVanter = () => {
  const { t } = useTranslation('auth')
  const { t: tCommon } = useTranslation('common')
  return (
    <div className="absolute bottom-4 left-0 right-0 z-10 flex flex-col items-center gap-0.5">
      <span className="text-lg font-bold text-[#920703]">{tCommon('brandFallback')}</span>
      <p className="text-xs text-zinc-500">
        {t('poweredBy')}{' '}
        <a
          href="https://vanter.net"
          target="_blank"
          rel="noopener noreferrer"
          className="font-semibold text-zinc-700 hover:underline"
        >
          Vanter
        </a>
      </p>
    </div>
  )
}
