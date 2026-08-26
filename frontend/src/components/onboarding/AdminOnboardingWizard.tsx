import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { SettingsService } from '@/lib/api'
import type { components } from '@/lib/backend-types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useTranslation } from '@/lib/i18n'

type SettingsPayload = components['schemas']['SettingsPayload']
type WizardStep = 'welcome' | 'businessName' | 'tables' | 'done'

export const AdminOnboardingWizard = () => {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const { data: settings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  })

  const [step, setStep] = useState<WizardStep>('welcome')
  const [businessName, setBusinessName] = useState('')
  const [totalTables, setTotalTables] = useState(1)

  const mutation = useMutation({
    mutationFn: (payload: SettingsPayload) => SettingsService.updateSettings(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] }),
  })

  const saveBusinessName = () => {
    if (!settings) return
    mutation.mutate(
      { ...settings, branding: { ...settings.branding, businessName } },
      { onSuccess: () => setStep('tables') }
    )
  }

  const saveTables = () => {
    if (!settings) return
    mutation.mutate(
      { ...settings, space: { totalTables } },
      { onSuccess: () => setStep('done') }
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-50 p-6">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-sm border border-zinc-100 p-8">
        {step === 'welcome' && (
          <div className="space-y-6 text-center">
            <h1 className="text-2xl font-bold text-[#7a1315]">{t('onboardingWelcomeTitle')}</h1>
            <p className="text-zinc-600">{t('onboardingWelcomeDescription')}</p>
            <Button className="w-full" onClick={() => setStep('businessName')}>
              {t('onboardingContinueButton')}
            </Button>
          </div>
        )}

        {step === 'businessName' && (
          <div className="space-y-6">
            <h1 className="text-xl font-bold">{t('onboardingBusinessNameTitle')}</h1>
            <p className="text-zinc-600 text-sm">{t('onboardingBusinessNameDescription')}</p>
            <div className="space-y-2">
              <Label htmlFor="onboarding-business-name">{t('businessNameLabel')}</Label>
              <Input
                id="onboarding-business-name"
                value={businessName}
                onChange={(e) => setBusinessName(e.target.value)}
                placeholder="Ember Fine Dining"
              />
            </div>
            {mutation.isError && (
              <p className="text-sm text-red-600">{t('onboardingSaveErrorMessage')}</p>
            )}
            <Button
              className="w-full"
              disabled={!businessName.trim() || mutation.isPending}
              onClick={saveBusinessName}
            >
              {mutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                t('onboardingContinueButton')
              )}
            </Button>
          </div>
        )}

        {step === 'tables' && (
          <div className="space-y-6">
            <h1 className="text-xl font-bold">{t('onboardingTablesTitle')}</h1>
            <p className="text-zinc-600 text-sm">{t('onboardingTablesDescription')}</p>
            <div className="space-y-2">
              <Label htmlFor="onboarding-total-tables">{t('totalTablesLabel')}</Label>
              <Input
                id="onboarding-total-tables"
                type="number"
                min={1}
                max={200}
                value={totalTables}
                onChange={(e) => setTotalTables(Number(e.target.value))}
              />
            </div>
            {mutation.isError && (
              <p className="text-sm text-red-600">{t('onboardingSaveErrorMessage')}</p>
            )}
            <Button
              className="w-full"
              disabled={totalTables < 1 || mutation.isPending}
              onClick={saveTables}
            >
              {mutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                t('onboardingContinueButton')
              )}
            </Button>
          </div>
        )}

        {step === 'done' && (
          <div className="space-y-6 text-center">
            <h1 className="text-2xl font-bold text-[#7a1315]">{t('onboardingDoneTitle')}</h1>
            <p className="text-zinc-600">{t('onboardingDoneDescription')}</p>
            <Link to="/admin/settings">
              <Button className="w-full">{t('onboardingFinishButton')}</Button>
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}
