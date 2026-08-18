import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { LoyaltyAccrualMode, LoyaltySettings as LoyaltySettingsPayload, SettingsResponseWithLoyalty } from '@/lib/api';
import { Gift, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const LOYALTY_DEFAULTS: LoyaltySettingsPayload = {
  enabled: false,
  accrualMode: 'BY_VISIT',
  pointsPerVisit: 10,
  pointsPerCurrencyUnit: 1,
  plataThreshold: 100,
  oroThreshold: 500,
  platinoThreshold: 1500,
};

export const LoyaltySettings = () => {
  const queryClient = useQueryClient();

  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings() as Promise<SettingsResponseWithLoyalty>,
  });

  const [draftLoyalty, setDraftLoyalty] = useState<Partial<LoyaltySettingsPayload> | undefined>(undefined);

  const currentEnabled = draftLoyalty?.enabled ?? settings?.loyalty?.enabled ?? LOYALTY_DEFAULTS.enabled;
  const currentAccrualMode = draftLoyalty?.accrualMode ?? settings?.loyalty?.accrualMode ?? LOYALTY_DEFAULTS.accrualMode;
  const currentPointsPerVisit = draftLoyalty?.pointsPerVisit ?? settings?.loyalty?.pointsPerVisit ?? LOYALTY_DEFAULTS.pointsPerVisit;
  const currentPointsPerCurrencyUnit = draftLoyalty?.pointsPerCurrencyUnit ?? settings?.loyalty?.pointsPerCurrencyUnit ?? LOYALTY_DEFAULTS.pointsPerCurrencyUnit;
  const currentPlataThreshold = draftLoyalty?.plataThreshold ?? settings?.loyalty?.plataThreshold ?? LOYALTY_DEFAULTS.plataThreshold;
  const currentOroThreshold = draftLoyalty?.oroThreshold ?? settings?.loyalty?.oroThreshold ?? LOYALTY_DEFAULTS.oroThreshold;
  const currentPlatinoThreshold = draftLoyalty?.platinoThreshold ?? settings?.loyalty?.platinoThreshold ?? LOYALTY_DEFAULTS.platinoThreshold;

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsResponseWithLoyalty) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {
      setDraftLoyalty(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success("Configuración guardada con éxito");
    },
    onError: () => {
      toast.error("Error al guardar la configuración");
    }
  });

  const updateDraft = (patch: Partial<LoyaltySettingsPayload>) => {
    setDraftLoyalty({
      enabled: currentEnabled,
      accrualMode: currentAccrualMode,
      pointsPerVisit: currentPointsPerVisit,
      pointsPerCurrencyUnit: currentPointsPerCurrencyUnit,
      plataThreshold: currentPlataThreshold,
      oroThreshold: currentOroThreshold,
      platinoThreshold: currentPlatinoThreshold,
      ...patch,
    });
  };

  const handleSave = () => {
    if (!settings) return;

    const payloadToSave: SettingsResponseWithLoyalty = {
      ...settings,
      loyalty: {
        enabled: currentEnabled,
        accrualMode: currentAccrualMode,
        pointsPerVisit: currentPointsPerVisit,
        pointsPerCurrencyUnit: currentPointsPerCurrencyUnit,
        plataThreshold: currentPlataThreshold,
        oroThreshold: currentOroThreshold,
        platinoThreshold: currentPlatinoThreshold,
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftLoyalty(undefined);
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">Cargando configuraciones...</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <Gift className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">Fidelización</CardTitle>
          <CardDescription>Configura el programa de puntos y niveles para tus clientes.</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="loyaltyEnabled">Programa habilitado</Label>
              <p className="text-xs text-muted-foreground">
                Acumula puntos por cada mesa pagada y asigna niveles a tus clientes.
              </p>
            </div>
            <Switch
              id="loyaltyEnabled"
              checked={currentEnabled}
              onCheckedChange={(checked) => updateDraft({ enabled: checked })}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="loyaltyAccrualMode">Modo de acumulación</Label>
            <Select
              value={currentAccrualMode}
              onValueChange={(value) => updateDraft({ accrualMode: value as LoyaltyAccrualMode })}
            >
              <SelectTrigger id="loyaltyAccrualMode" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="BY_VISIT">Puntos fijos por visita</SelectItem>
                <SelectItem value="BY_AMOUNT_SPENT">Puntos por monto gastado</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {currentAccrualMode === 'BY_VISIT' ? (
            <div className="space-y-2">
              <Label htmlFor="loyaltyPointsPerVisit">Puntos por visita</Label>
              <Input
                id="loyaltyPointsPerVisit"
                type="number"
                min={0}
                value={currentPointsPerVisit}
                onChange={(e) => updateDraft({ pointsPerVisit: Number(e.target.value) })}
                className="focus-visible:ring-[#7a1315]"
              />
            </div>
          ) : (
            <div className="space-y-2">
              <Label htmlFor="loyaltyPointsPerCurrencyUnit">Puntos por unidad monetaria gastada</Label>
              <Input
                id="loyaltyPointsPerCurrencyUnit"
                type="number"
                min={0}
                step="0.1"
                value={currentPointsPerCurrencyUnit}
                onChange={(e) => updateDraft({ pointsPerCurrencyUnit: Number(e.target.value) })}
                className="focus-visible:ring-[#7a1315]"
              />
            </div>
          )}

          <div className="space-y-2">
            <Label>Umbrales de nivel (puntos acumulados)</Label>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-1">
                <Label htmlFor="loyaltyPlataThreshold" className="text-xs text-muted-foreground">Plata</Label>
                <Input
                  id="loyaltyPlataThreshold"
                  type="number"
                  min={0}
                  value={currentPlataThreshold}
                  onChange={(e) => updateDraft({ plataThreshold: Number(e.target.value) })}
                  className="focus-visible:ring-[#7a1315]"
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="loyaltyOroThreshold" className="text-xs text-muted-foreground">Oro</Label>
                <Input
                  id="loyaltyOroThreshold"
                  type="number"
                  min={0}
                  value={currentOroThreshold}
                  onChange={(e) => updateDraft({ oroThreshold: Number(e.target.value) })}
                  className="focus-visible:ring-[#7a1315]"
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="loyaltyPlatinoThreshold" className="text-xs text-muted-foreground">Platino</Label>
                <Input
                  id="loyaltyPlatinoThreshold"
                  type="number"
                  min={0}
                  value={currentPlatinoThreshold}
                  onChange={(e) => updateDraft({ platinoThreshold: Number(e.target.value) })}
                  className="focus-visible:ring-[#7a1315]"
                />
              </div>
            </div>
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftLoyalty === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          Deshacer cambios
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftLoyalty === undefined || updateSettingsMutation.isPending}
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
