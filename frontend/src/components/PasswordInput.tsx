import * as React from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { useTranslation } from '@/lib/i18n'
import { cn } from '@/lib/utils'

type PasswordInputProps = Omit<React.ComponentProps<'input'>, 'type'>

/** Password field with a reveal toggle (eye icon): gray while hidden, `#8c1717` while shown. */
export const PasswordInput = ({ className, ...props }: PasswordInputProps) => {
  const { t } = useTranslation('common')
  const [visible, setVisible] = React.useState(false)
  const Icon = visible ? EyeOff : Eye

  return (
    <div className="relative w-full">
      <Input {...props} type={visible ? 'text' : 'password'} className={cn('pr-11 [&::-ms-clear]:hidden [&::-ms-reveal]:hidden', className)} />
      <button
        type="button"
        tabIndex={-1}
        aria-label={visible ? t('hidePasswordAria') : t('showPasswordAria')}
        aria-pressed={visible}
        onClick={() => setVisible((v) => !v)}
        className={cn(
          'absolute right-3 top-1/2 flex h-6 w-6 -translate-y-1/2 cursor-pointer items-center justify-center transition-colors',
          visible ? 'text-[#8c1717]' : 'text-zinc-400 hover:text-zinc-500'
        )}
      >
        <Icon className="h-5 w-5" aria-hidden="true" />
      </button>
    </div>
  )
}
