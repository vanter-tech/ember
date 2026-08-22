import { useTranslation } from '@/lib/i18n'

export const StaffHeader = () => {
  const { t } = useTranslation('admin')
  return (
    <div className="flex flex-col gap-1">
      <h1 className="text-3xl font-bold tracking-tight text-foreground">
        {t('staffPageTitle')}
      </h1>
      <p className="text-sm text-muted-foreground">
        {t('staffPageSubtitle')}
      </p>
    </div>
  )
}
