import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { SettingsService } from '@/lib/api'
import type { components } from '@/lib/backend-types'
import { House, Loader2 } from 'lucide-react'
import toast from 'react-hot-toast'

import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { useTranslation } from '@/lib/i18n'

type SettingsPayload = components['schemas']['SettingsPayload']

export const BrandingSettings = () => {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const { data: settings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  })

  const [draftBranding, setDraftBranding] = useState<
    Partial<SettingsPayload['branding']>
  >({})

  const updateSettingsMutation = useMutation({
    mutationFn: (updatePayload: SettingsPayload) =>
      SettingsService.updateSettings(updatePayload),
    onSuccess: () => {
      setDraftBranding(undefined)
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] })
      toast.success(t('settingsSavedToast'))
    },
    onError: () => {
      toast.error(t('settingsSaveErrorToast'))
    },
  })

  const handleSave = () => {
    if (!settings) return
    const payloadToSave: SettingsPayload = {
      ...settings,
      branding: {
        ...settings.branding,
        ...draftBranding,
      },
    }
    updateSettingsMutation.mutate(payloadToSave)
  }

  const handleUndo = () => {
    setDraftBranding(undefined)
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flew-row items-center gap-4 space-y-0 pt-6 pl-6 pr-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <House className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('brandingCardTitle')}</CardTitle>
          <CardDescription>{t('brandingCardDescription')}</CardDescription>
        </div>
      </CardHeader>
      <div className="border-t w-full m-auto border-[#7a1315]/20"></div>
      <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6 p-6">
        <div className="flex flex-col space-y-6">
          <div className="space-y-2">
            <Label>{t('businessNameLabel')}</Label>
            <Input
              placeholder="Ember Fine Dining"
              id="businessName"
              value={
                draftBranding?.businessName ??
                settings?.branding?.businessName ??
                ''
              }
              onChange={(e) =>
                setDraftBranding({
                  ...draftBranding,
                  businessName: e.target.value,
                })
              }
            />
          </div>

          <div className="space-y-2">
            <Label>{t('legalNameLabel')}</Label>
            <Input
              placeholder="Ember Gastronomía S.A. de C.V."
              value={
                draftBranding?.legalName ?? settings?.branding?.legalName ?? ''
              }
              onChange={(e) =>
                setDraftBranding({
                  ...draftBranding,
                  legalName: e.target.value,
                })
              }
            />
          </div>

          {/* Sub-Grid para 2 campos en la misma fila */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>{t('taxIdLabel')}</Label>
              <Input
                placeholder={t('rucPlaceholder')}
                value={draftBranding?.ruc ?? settings?.branding?.ruc ?? ''}
                onChange={(e) =>
                  setDraftBranding({
                    ...draftBranding,
                    ruc: e.target.value,
                  })
                }
              />
            </div>
            <div className="space-y-2">
              <Label>{t('phoneLabel')}</Label>
              <Input
                placeholder={t('phonePlaceholder')}
                value={draftBranding?.phone ?? settings?.branding?.phone ?? ''}
                onChange={(e) =>
                  setDraftBranding({
                    ...draftBranding,
                    phone: e.target.value,
                  })
                }
              />
            </div>
          </div>

          {/* Campo Full Width */}
          <div className="space-y-2">
            <Label>{t('taxAddressLabel')}</Label>
            <Input
              placeholder={t('addressPlaceholder')}
              value={
                draftBranding?.address ?? settings?.branding?.address ?? ''
              }
              onChange={(e) =>
                setDraftBranding({
                  ...draftBranding,
                  address: e.target.value,
                })
              }
            />
          </div>
        </div>
        <div className="flex flex-col space-y-6">

          <div className="space-y-2">
            <Label>{t('businessHoursLabel')}</Label>
            <div className="flex items-center gap-3">
              <Input type="time" defaultValue="12:00"
              value={draftBranding?.openingTime ?? settings?.branding?.openingTime ?? ''}
              onChange={(e) =>
                setDraftBranding({
                    ...draftBranding,
                    openingTime: e.target.value,
                })
              }
              />
              <span className="text-sm text-zinc-500 font-medium">{t('timeRangeSeparator')}</span>
              <Input type="time" defaultValue="23:00"
              value={draftBranding?.closingTime ?? settings?.branding?.closingTime ?? ''}
              onChange={(e) =>
                setDraftBranding({
                    ...draftBranding,
                    closingTime: e.target.value,
                })
              } 
              />
            </div>
          </div>

          <div className="space-y-2">
            
            <div className="grid grid-cols-1 gap-4">
                <Label>{t('wifiNameLabel')}</Label>
              <Input placeholder="Ember_Guest"
              value={draftBranding?.wifiName ?? settings?.branding?.wifiName ?? ''}
              onChange={(e) =>
                setDraftBranding({
                    ...draftBranding,
                    wifiName: e.target.value,
                })
              } />
              <Label>{t('primaryColorLabel')}</Label>
              <Input placeholder={t('colorPlaceholder')}
              value={draftBranding?.primaryThemeColor ?? settings?.branding?.primaryThemeColor ?? ''}
              onChange={(e) =>
                setDraftBranding({
                    ...draftBranding,
                    primaryThemeColor: e.target.value,
                })
              } />
            </div>
          </div>
        </div>
      </CardContent>
      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={
            draftBranding === undefined || updateSettingsMutation.isPending
          }
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          {t('undoChangesButton')}
        </Button>
        <Button
          onClick={handleSave}
          disabled={
            setDraftBranding === undefined || updateSettingsMutation.isPending
          }
          className="hover:bg-[#b91016] text-white p-4"
        >
          {updateSettingsMutation.isPending ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {t('savingEllipsisLabel')}
            </>
          ) : (
            t('saveSettingsButton')
          )}
        </Button>
      </CardFooter>
    </Card>
  )
}
