import { useLocaleStore } from '@/store/localeStore'
import { dictionaries, type Namespace } from '@/locales'
import type { Locale, TranslationVars } from '@/locales/types'

const interpolate = (template: string, vars?: TranslationVars): string => {
  if (!vars) return template
  return Object.entries(vars).reduce(
    (result, [key, value]) => result.replaceAll(`{{${key}}}`, String(value)),
    template
  )
}

export const useTranslation = <N extends Namespace>(namespace: N) => {
  const rawLocale = useLocaleStore((state) => state.locale)
  const setLocale = useLocaleStore((state) => state.setLocale)
  // Defends against a corrupted/unrecognized value in localStorage — anything
  // that isn't exactly 'en' collapses to the 'es' default rather than crashing.
  const locale: Locale = rawLocale === 'en' ? 'en' : 'es'

  const t = (
    key: keyof (typeof dictionaries)['es'][N],
    vars?: TranslationVars
  ): string => {
    const dict = dictionaries[locale][namespace] as Record<string, string>
    const value = dict[key as string]
    if (value === undefined) {
      if (import.meta.env.DEV) {
        console.warn(
          `[i18n] missing key "${String(key)}" in namespace "${namespace}" (${locale})`
        )
      }
      const esDict = dictionaries.es[namespace] as Record<string, string>
      return esDict[key as string] ?? String(key)
    }
    return interpolate(value, vars)
  }

  return { t, locale, setLocale }
}
