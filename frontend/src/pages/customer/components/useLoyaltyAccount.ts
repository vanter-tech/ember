import { useQuery } from '@tanstack/react-query'
import { loyaltyAccountService } from '@/lib/api'
import { useSettingStore } from '@/store/settingStore'

export const useLoyaltyAccount = () => {
  const { settings } = useSettingStore()
  const loyaltyEnabled = settings?.loyalty?.enabled ?? false

  const { data: account } = useQuery({
    queryKey: ['loyaltyAccount', 'me'],
    queryFn: loyaltyAccountService.me,
    enabled: loyaltyEnabled,
    retry: false,
  })

  return { loyaltyEnabled, account }
}
