import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { components } from '@/lib/backend-types';
import { LayoutDashboard, Loader2 } from 'lucide-react'; 
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { useTranslation } from '@/lib/i18n';

type SettingsPayload = components['schemas']['SettingsPayload'];

export const SpacesSettings = () => {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  
  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  });


  const [draftTables, setDraftTables] = useState<number | undefined>(undefined);

  const currentTablesValue = draftTables ?? settings?.space?.totalTables ?? 10;

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsPayload) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {

      setDraftTables(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success(t('settingsSavedToast'));
    },
    onError: () => {
      toast.error(t('settingsSaveErrorToast'));
    }
  });

  const handleSave = () => {
    if (!settings) return; 

    const payloadToSave: SettingsPayload = {
      ...settings, 
      space: {     
        totalTables: currentTablesValue
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftTables(undefined);
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">{t('loadingSettingsLabel')}</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <LayoutDashboard className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('spaceCardTitle')}</CardTitle>
          <CardDescription>{t('spaceCardDescription')}</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-3">
          <Label htmlFor="totalTables">{t('totalTablesLabel')}</Label>
          <Input
            id="totalTables"
            type="number"
            min="1"
            max="200"
            value={currentTablesValue}
            onChange={(e) => setDraftTables(Number(e.target.value))}
            className="focus-visible:ring-[#7a1315]"
          />
          <p className="text-xs text-muted-foreground">
            {t('totalTablesDescription')}
          </p>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftTables === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          {t('undoChangesButton')}
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftTables === undefined || updateSettingsMutation.isPending}
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