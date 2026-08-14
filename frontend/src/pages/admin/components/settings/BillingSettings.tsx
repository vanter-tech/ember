import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { components } from '@/lib/backend-types';
import { Receipt, Loader2, Plus, X } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';

type SettingsPayload = components['schemas']['SettingsPayload'];
type BillingSettings = components['schemas']['BillingSettings'];
type TaxRule = components['schemas']['TaxRule'];

export const BillingSettings = () => {
  const queryClient = useQueryClient();

  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  });

  const [draftBilling, setDraftBilling] = useState<Partial<BillingSettings> | undefined>(undefined);
  const [newTipValue, setNewTipValue] = useState('');

  const currentCurrencySymbol = draftBilling?.currencySymbol ?? settings?.billing?.currencySymbol ?? 'S/';
  const currentTaxRate = draftBilling?.taxRate ?? settings?.billing?.taxRate ?? 0;
  const currentTaxIncluded = draftBilling?.taxIncludeInMenuPrice ?? settings?.billing?.taxIncludeInMenuPrice ?? false;
  const currentTipPercentages = draftBilling?.suggestedTipPercentage ?? settings?.billing?.suggestedTipPercentage ?? [];
  const currentTaxRules = draftBilling?.taxRules ?? settings?.billing?.taxRules ?? [];

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsPayload) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {
      setDraftBilling(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success("Configuración guardada con éxito");
    },
    onError: () => {
      toast.error("Error al guardar la configuración");
    }
  });

  const updateDraft = (patch: Partial<BillingSettings>) => {
    setDraftBilling({
      currencySymbol: currentCurrencySymbol,
      taxRate: currentTaxRate,
      taxIncludeInMenuPrice: currentTaxIncluded,
      suggestedTipPercentage: currentTipPercentages,
      taxRules: currentTaxRules,
      ...patch,
    });
  };

  const handleAddTip = () => {
    const value = Number(newTipValue);
    if (!newTipValue || Number.isNaN(value) || value <= 0) return;
    updateDraft({ suggestedTipPercentage: [...currentTipPercentages, value] });
    setNewTipValue('');
  };

  const handleRemoveTip = (index: number) => {
    updateDraft({ suggestedTipPercentage: currentTipPercentages.filter((_, i) => i !== index) });
  };

  const handleAddTaxRule = () => {
    updateDraft({ taxRules: [...currentTaxRules, { name: '', rate: 0, includedInPrice: false }] });
  };

  const handleRemoveTaxRule = (index: number) => {
    updateDraft({ taxRules: currentTaxRules.filter((_, i) => i !== index) });
  };

  const handleTaxRuleChange = (index: number, patch: Partial<TaxRule>) => {
    updateDraft({
      taxRules: currentTaxRules.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)),
    });
  };

  const handleSave = () => {
    if (!settings) return;

    const payloadToSave: SettingsPayload = {
      ...settings,
      billing: {
        currencySymbol: currentCurrencySymbol,
        taxRate: currentTaxRate,
        taxIncludeInMenuPrice: currentTaxIncluded,
        suggestedTipPercentage: currentTipPercentages,
        taxRules: currentTaxRules,
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftBilling(undefined);
    setNewTipValue('');
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">Cargando configuraciones...</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <Receipt className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">Facturación</CardTitle>
          <CardDescription>Configura moneda, impuestos y propinas sugeridas.</CardDescription>
        </div>
      </CardHeader>

      <CardContent className="space-y-8">
        <div className="max-w-md grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="currencySymbol">Símbolo de moneda</Label>
            <Input
              id="currencySymbol"
              value={currentCurrencySymbol}
              onChange={(e) => updateDraft({ currencySymbol: e.target.value })}
              className="focus-visible:ring-[#7a1315]"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="taxRate">Tasa de impuesto (%)</Label>
            <Input
              id="taxRate"
              type="number"
              min="0"
              max="100"
              step="0.01"
              value={currentTaxRate}
              onChange={(e) => updateDraft({ taxRate: Number(e.target.value) })}
              className="focus-visible:ring-[#7a1315]"
            />
          </div>
        </div>

        <div className="flex items-center justify-between max-w-md">
          <div className="space-y-0.5">
            <Label htmlFor="taxIncludeInMenuPrice">Impuesto incluido en el precio del menú</Label>
            <p className="text-xs text-muted-foreground">
              Si está activo, los precios del menú ya incluyen el impuesto.
            </p>
          </div>
          <Switch
            id="taxIncludeInMenuPrice"
            checked={currentTaxIncluded}
            onCheckedChange={(checked) => updateDraft({ taxIncludeInMenuPrice: checked })}
          />
        </div>

        <div className="max-w-md space-y-3">
          <Label>Propinas sugeridas (%)</Label>
          <div className="flex flex-wrap gap-2">
            {currentTipPercentages.map((tip, index) => (
              <span
                key={index}
                className="flex items-center gap-1 bg-zinc-100 text-zinc-700 text-sm rounded-full px-3 py-1"
              >
                {tip}%
                <button
                  type="button"
                  onClick={() => handleRemoveTip(index)}
                  className="text-zinc-400 hover:text-zinc-700"
                  aria-label={`Eliminar propina sugerida ${tip}%`}
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <Input
              type="number"
              min="0"
              max="100"
              placeholder="Ej. 10"
              value={newTipValue}
              onChange={(e) => setNewTipValue(e.target.value)}
              className="focus-visible:ring-[#7a1315]"
            />
            <Button type="button" variant="outline" onClick={handleAddTip}>
              <Plus className="w-4 h-4 mr-1" />
              Agregar
            </Button>
          </div>
        </div>

        <div className="space-y-3">
          <Label>Reglas de impuestos</Label>
          <div className="space-y-3">
            {currentTaxRules.map((rule, index) => (
              <div key={index} className="flex flex-wrap items-end gap-3 border border-zinc-200 rounded-lg p-3">
                <div className="space-y-1">
                  <Label htmlFor={`taxRuleName-${index}`} className="text-xs">Nombre</Label>
                  <Input
                    id={`taxRuleName-${index}`}
                    value={rule.name ?? ''}
                    onChange={(e) => handleTaxRuleChange(index, { name: e.target.value })}
                    className="w-40 focus-visible:ring-[#7a1315]"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor={`taxRuleRate-${index}`} className="text-xs">Tasa (%)</Label>
                  <Input
                    id={`taxRuleRate-${index}`}
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    value={rule.rate ?? 0}
                    onChange={(e) => handleTaxRuleChange(index, { rate: Number(e.target.value) })}
                    className="w-28 focus-visible:ring-[#7a1315]"
                  />
                </div>
                <div className="flex items-center gap-2 pb-2">
                  <Switch
                    id={`taxRuleIncluded-${index}`}
                    checked={rule.includedInPrice ?? false}
                    onCheckedChange={(checked) => handleTaxRuleChange(index, { includedInPrice: checked })}
                  />
                  <Label htmlFor={`taxRuleIncluded-${index}`} className="text-xs">Incluido en precio</Label>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => handleRemoveTaxRule(index)}
                  className="text-zinc-500 hover:text-red-600 ml-auto"
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            ))}
          </div>
          <Button type="button" variant="outline" onClick={handleAddTaxRule}>
            <Plus className="w-4 h-4 mr-1" />
            Agregar regla
          </Button>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftBilling === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          Deshacer cambios
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftBilling === undefined || updateSettingsMutation.isPending}
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
