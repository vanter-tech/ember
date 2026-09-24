import { useTranslation } from '@/lib/i18n'

/** Footer credit pinned to the bottom edge of a `relative` auth container. */
export const PoweredByVanter = () => {
  const { t } = useTranslation('auth')
  return (
    <p className="absolute bottom-4 left-0 right-0 z-10 text-center text-xs text-zinc-500">
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
  )
}
