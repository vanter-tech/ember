import { useState } from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useTranslation } from '@/lib/i18n'
import { cn } from '@/lib/utils'
import type { Locale } from '@/locales/types'

const OPTIONS: { value: Locale; labelKey: 'languageSpanish' | 'languageEnglish' }[] = [
  { value: 'es', labelKey: 'languageSpanish' },
  { value: 'en', labelKey: 'languageEnglish' },
]

/** Circular language picker pinned to the bottom-right corner of the auth screens. */
export const LanguageFab = () => {
  const { t, locale, setLocale } = useTranslation('common')
  const [open, setOpen] = useState(false)

  return (
    <div className="fixed bottom-6 right-6 z-20">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            type="button"
            data-testid="language-fab"
            aria-label={t('languageSwitcherLabel')}
            className="flex h-12 w-12 cursor-pointer items-center justify-center rounded-full bg-[#8c1717] text-sm font-bold text-white shadow-lg transition hover:brightness-110"
          >
            {locale.toUpperCase()}
          </button>
        </PopoverTrigger>
        <PopoverContent align="end" side="top" className="w-40 p-1">
          {OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => {
                setLocale(option.value)
                setOpen(false)
              }}
              className={cn(
                'w-full cursor-pointer rounded-md px-3 py-2 text-left text-sm hover:bg-zinc-100',
                locale === option.value && 'font-semibold text-[#8c1717]'
              )}
            >
              {t(option.labelKey)}
            </button>
          ))}
        </PopoverContent>
      </Popover>
    </div>
  )
}
