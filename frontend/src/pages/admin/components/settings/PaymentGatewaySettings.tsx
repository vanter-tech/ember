import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { components } from '@/lib/backend-types';
import { CreditCard, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';
import { useTranslation } from '@/lib/i18n';

type SettingsPayload = components['schemas']['SettingsPayload'];
type PaymentGatewaySettings = components['schemas']['PaymentGatewaySettings'];

export const PaymentGatewaySettings = () => {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();

  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  });

  const [draftGateway, setDraftGateway] = useState<Partial<PaymentGatewaySettings> | undefined>(undefined);

  const currentEnabled = draftGateway?.enabled ?? settings?.paymentGateway?.enabled ?? false;
  const currentProvider = draftGateway?.provider ?? settings?.paymentGateway?.provider ?? '';
  const currentPublicKey = draftGateway?.publicKey ?? settings?.paymentGateway?.publicKey ?? '';
  const currentSecretRef = draftGateway?.secretRef ?? settings?.paymentGateway?.secretRef ?? '';

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsPayload) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {
      setDraftGateway(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success(t('settingsSavedToast'));
    },
    onError: () => {
      toast.error(t('settingsSaveErrorToast'));
    }
  });

  const updateDraft = (patch: Partial<PaymentGatewaySettings>) => {
    setDraftGateway({
      enabled: currentEnabled,
      provider: currentProvider,
      publicKey: currentPublicKey,
      secretRef: currentSecretRef,
      ...patch,
    });
  };

  const handleSave = () => {
    if (!settings) return;

    const payloadToSave: SettingsPayload = {
      ...settings,
      paymentGateway: {
        enabled: currentEnabled,
        provider: currentProvider,
        publicKey: currentPublicKey,
        secretRef: currentSecretRef,
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftGateway(undefined);
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">{t('loadingSettingsLabel')}</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <CreditCard className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('paymentGatewayCardTitle')}</CardTitle>
          <CardDescription>{t('paymentGatewayCardDescription')}</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="gatewayEnabled">{t('gatewayEnabledLabel')}</Label>
              <p className="text-xs text-muted-foreground">
                {t('paymentGatewayDescription', { brand: 'Ember' })}
              </p>
            </div>
            <Switch
              id="gatewayEnabled"
              checked={currentEnabled}
              onCheckedChange={(checked) => updateDraft({ enabled: checked })}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="gatewayProvider">{t('providerLabel')}</Label>
            <Input
              id="gatewayProvider"
              placeholder={t('providerPlaceholder')}
              value={currentProvider}
              onChange={(e) => updateDraft({ provider: e.target.value })}
              className="focus-visible:ring-[#7a1315]"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="gatewayPublicKey">{t('publicKeyLabel')}</Label>
            <Input
              id="gatewayPublicKey"
              placeholder={t('publicKeyPlaceholder')}
              value={currentPublicKey}
              onChange={(e) => updateDraft({ publicKey: e.target.value })}
              className="focus-visible:ring-[#7a1315]"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="gatewaySecretRef">{t('secretRefLabel')}</Label>
            <Input
              id="gatewaySecretRef"
              placeholder={t('secretRefPlaceholder')}
              value={currentSecretRef}
              onChange={(e) => updateDraft({ secretRef: e.target.value })}
              className="focus-visible:ring-[#7a1315]"
            />
            <p className="text-xs text-muted-foreground">
              {t('secretRefWarning')}
            </p>
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftGateway === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          {t('undoChangesButton')}
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftGateway === undefined || updateSettingsMutation.isPending}
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
