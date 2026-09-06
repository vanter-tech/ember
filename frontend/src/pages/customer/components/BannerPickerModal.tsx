import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Check, Loader2 } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { userProfileService, type UserProfileResponse } from '@/lib/api'
import {
  BANNER_KEYS,
  BANNER_PRESETS,
  type BannerKey,
} from '@/lib/bannerPresets'
import { useTranslation } from '@/lib/i18n'

export const BannerPickerModal = ({
  open,
  onClose,
  current,
}: {
  open: boolean
  onClose: () => void
  current: BannerKey
}) => {
  const { t } = useTranslation('customer')
  const queryClient = useQueryClient()
  const [pending, setPending] = useState<BannerKey | null>(null)

  const mutation = useMutation({
    mutationFn: (key: BannerKey) => userProfileService.updateBanner(key),
    onMutate: (key) => setPending(key),
    onSuccess: (profile: UserProfileResponse) => {
      queryClient.setQueryData(['me'], profile)
      onClose()
    },
    onError: () => toast.error(t('genericErrorToast')),
    onSettled: () => setPending(null),
  })

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader>
          <DialogTitle>{t('bannerPickerTitle')}</DialogTitle>
          <DialogDescription>{t('bannerPickerDescription')}</DialogDescription>
        </DialogHeader>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {BANNER_KEYS.map((key) => {
            const isCurrent = key === current
            const isPending = pending === key
            return (
              <button
                key={key}
                type="button"
                disabled={mutation.isPending}
                onClick={() => mutation.mutate(key)}
                aria-pressed={isCurrent}
                className={`relative flex aspect-[4/3] items-center justify-center overflow-hidden rounded-2xl border-2 text-white transition-all ${
                  BANNER_PRESETS[key].gradient
                } ${
                  isCurrent
                    ? 'border-[#8c1717] ring-2 ring-[#8c1717]/30'
                    : 'border-transparent hover:border-[#8c1717]/40'
                }`}
              >
                <span className="absolute bottom-1.5 left-2 text-xs font-medium drop-shadow">
                  {t(BANNER_PRESETS[key].labelKey)}
                </span>
                {isPending ? (
                  <Loader2 className="h-5 w-5 animate-spin" />
                ) : (
                  isCurrent && (
                    <span className="grid h-6 w-6 place-items-center rounded-full bg-white text-[#8c1717]">
                      <Check className="h-4 w-4" />
                    </span>
                  )
                )}
              </button>
            )
          })}
        </div>
      </DialogContent>
    </Dialog>
  )
}
