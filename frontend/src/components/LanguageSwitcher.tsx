import { useTranslation } from '@/lib/i18n'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { Locale } from '@/locales/types'

export const LanguageSwitcher = () => {
  const { t, locale, setLocale } = useTranslation('common')

  return (
    <Select value={locale} onValueChange={(value) => setLocale(value as Locale)}>
      <SelectTrigger
        data-testid="language-switcher-trigger"
        aria-label={t('languageSwitcherLabel')}
        size="sm"
      >
        <SelectValue>{locale.toUpperCase()}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="es">{t('languageSpanish')}</SelectItem>
        <SelectItem value="en">{t('languageEnglish')}</SelectItem>
      </SelectContent>
    </Select>
  )
}
