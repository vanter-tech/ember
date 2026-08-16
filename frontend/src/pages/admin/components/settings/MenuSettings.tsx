import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { components } from '@/lib/backend-types';
import { UtensilsCrossed, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';

type SettingsPayload = components['schemas']['SettingsPayload'];
type MenuSettings = components['schemas']['MenuSettings'];

export const MenuSettings = () => {
  const queryClient = useQueryClient();

  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  });

  const [draftMenu, setDraftMenu] = useState<Partial<MenuSettings> | undefined>(undefined);

  const currentShowOutOfStock = draftMenu?.showOutOfStockItems ?? settings?.menu?.showOutOfStockItems ?? true;
  const currentEnableSearch = draftMenu?.enableItemSearch ?? settings?.menu?.enableItemSearch ?? true;

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsPayload) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {
      setDraftMenu(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success("Configuración guardada con éxito");
    },
    onError: () => {
      toast.error("Error al guardar la configuración");
    }
  });

  const handleToggle = (field: keyof MenuSettings, value: boolean) => {
    setDraftMenu({
      showOutOfStockItems: currentShowOutOfStock,
      enableItemSearch: currentEnableSearch,
      [field]: value,
    });
  };

  const handleSave = () => {
    if (!settings) return;

    const payloadToSave: SettingsPayload = {
      ...settings,
      menu: {
        showOutOfStockItems: currentShowOutOfStock,
        enableItemSearch: currentEnableSearch,
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftMenu(undefined);
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">Cargando configuraciones...</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <UtensilsCrossed className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">Menú Digital</CardTitle>
          <CardDescription>Controla la visibilidad y búsqueda de los platos.</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="showOutOfStockItems">Mostrar platos agotados</Label>
              <p className="text-xs text-muted-foreground">
                Los clientes verán los platos sin stock, marcados como no disponibles.
              </p>
            </div>
            <Switch
              id="showOutOfStockItems"
              checked={currentShowOutOfStock}
              onCheckedChange={(checked) => handleToggle('showOutOfStockItems', checked)}
            />
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="enableItemSearch">Habilitar búsqueda de platos</Label>
              <p className="text-xs text-muted-foreground">
                Muestra una barra de búsqueda en el menú digital del cliente.
              </p>
            </div>
            <Switch
              id="enableItemSearch"
              checked={currentEnableSearch}
              onCheckedChange={(checked) => handleToggle('enableItemSearch', checked)}
            />
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftMenu === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          Deshacer cambios
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftMenu === undefined || updateSettingsMutation.isPending}
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
  );
};
