import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { components } from '@/lib/backend-types';
import { Printer, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';
import { useTranslation } from '@/lib/i18n';

type SettingsPayload = components['schemas']['SettingsPayload'];
type HardwareSettings = components['schemas']['HardwareSettings'];

export const HardwareSettings = () => {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();

  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  });

  const [draftHardware, setDraftHardware] = useState<Partial<HardwareSettings> | undefined>(undefined);

  const currentAutoPrintTickets = draftHardware?.autoPrintTickets ?? settings?.hardware?.autoPrintTickets ?? false;
  const currentPrintCustomerReceipt = draftHardware?.printCustomerReceipt ?? settings?.hardware?.printCustomerReceipt ?? false;

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsPayload) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {
      setDraftHardware(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success("Configuración guardada con éxito");
    },
    onError: () => {
      toast.error("Error al guardar la configuración");
    }
  });

  const handleToggle = (field: keyof HardwareSettings, value: boolean) => {
    setDraftHardware({
      autoPrintTickets: currentAutoPrintTickets,
      printCustomerReceipt: currentPrintCustomerReceipt,
      [field]: value,
    });
  };

  const handleSave = () => {
    if (!settings) return;

    const payloadToSave: SettingsPayload = {
      ...settings,
      hardware: {
        autoPrintTickets: currentAutoPrintTickets,
        printCustomerReceipt: currentPrintCustomerReceipt,
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftHardware(undefined);
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">{t('loadingSettingsLabel')}</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <Printer className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('hardwareCardTitle')}</CardTitle>
          <CardDescription>{t('hardwareCardDescription')}</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="autoPrintTickets">{t('autoPrintTicketsLabel')}</Label>
              <p className="text-xs text-muted-foreground">
                {t('autoPrintTicketsDescription')}
              </p>
            </div>
            <Switch
              id="autoPrintTickets"
              checked={currentAutoPrintTickets}
              onCheckedChange={(checked) => handleToggle('autoPrintTickets', checked)}
            />
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="printCustomerReceipt">{t('printCustomerReceiptLabel')}</Label>
              <p className="text-xs text-muted-foreground">
                {t('printCustomerReceiptDescription')}
              </p>
            </div>
            <Switch
              id="printCustomerReceipt"
              checked={currentPrintCustomerReceipt}
              onCheckedChange={(checked) => handleToggle('printCustomerReceipt', checked)}
            />
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftHardware === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          {t('undoChangesButton')}
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftHardware === undefined || updateSettingsMutation.isPending}
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
  );
};
