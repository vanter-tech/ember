import * as React from 'react'
import { Input } from '@/components/ui/input'
import { useTranslation } from '@/lib/i18n'
import { cn } from '@/lib/utils'

type PasswordInputProps = Omit<React.ComponentProps<'input'>, 'type'>

/** Password field with a reveal toggle: the Ember flame, gray while hidden and red while shown. */
export const PasswordInput = ({ className, ...props }: PasswordInputProps) => {
  const { t } = useTranslation('common')
  const [visible, setVisible] = React.useState(false)
  const iconUrl = `url(${import.meta.env.BASE_URL}ember_flame.svg)`

  return (
    <div className="relative w-full">
      <Input {...props} type={visible ? 'text' : 'password'} className={cn('pr-11', className)} />
      <button
        type="button"
        tabIndex={-1}
        aria-label={visible ? t('hidePasswordAria') : t('showPasswordAria')}
        aria-pressed={visible}
        onClick={() => setVisible((v) => !v)}
        className="absolute right-3 top-1/2 flex h-6 w-6 -translate-y-1/2 cursor-pointer items-center justify-center"
      >
        <span
          aria-hidden="true"
          className={cn(
            'block h-5 w-4 transition-colors',
            visible ? 'bg-[#8c1717]' : 'bg-zinc-400 hover:bg-zinc-500'
          )}
          style={{
            maskImage: iconUrl,
            WebkitMaskImage: iconUrl,
            maskSize: 'contain',
            WebkitMaskSize: 'contain',
            maskRepeat: 'no-repeat',
            WebkitMaskRepeat: 'no-repeat',
            maskPosition: 'center',
            WebkitMaskPosition: 'center',
          }}
        />
      </button>
    </div>
  )
}
