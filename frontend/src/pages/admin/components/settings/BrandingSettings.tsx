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

type SettingsPayload = components['schemas']['SettingsPayload']

export const BrandingSettings = () => {
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
      toast.success('Configuración guardada con éxito')
    },
    onError: (error) => {
      console.error('Error al guardar', error)
      toast.error('Error al guardar la configuración')
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
          <CardTitle className="text-xl">Configuración de Marca</CardTitle>
          <CardDescription>Define tu marca</CardDescription>
        </div>
      </CardHeader>
      <div className="border-t w-full m-auto border-[#7a1315]/20"></div>
      <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6 p-6">
        <div className="flex flex-col space-y-6">
          <div className="space-y-2">
            <Label>Nombre Comercial</Label>
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
            <Label>Razón Social</Label>
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
              <Label>RUC / NIT</Label>
              <Input
                placeholder="800-123456-7"
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
              <Label>Teléfono</Label>
              <Input
                placeholder="+52 55 1234 5678"
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
            <Label>Dirección Fiscal</Label>
            <Input
              placeholder="Av. Gastronómica 452"
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
            <Label>Horario de Atención</Label>
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
              <span className="text-sm text-zinc-500 font-medium">a</span>
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
                <Label>WiFi Público (Nombre)</Label>
              <Input placeholder="Ember_Guest" 
              value={draftBranding?.wifiName ?? settings?.branding?.wifiName ?? ''}
              onChange={(e) =>
                setDraftBranding({
                    ...draftBranding,
                    wifiName: e.target.value,
                })
              } />
              <Label>Color primario</Label>
              <Input placeholder="#fff" 
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
          Deshacer cambios
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
              Guardando...
            </>
          ) : (
            'Guardar Cambios'
          )}
        </Button>
      </CardFooter>
    </Card>
  )
}
