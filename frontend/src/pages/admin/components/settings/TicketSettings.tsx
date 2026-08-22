import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { components } from '@/lib/backend-types';
import { FileText, Loader2, Receipt, ChefHat } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useTranslation } from '@/lib/i18n';

type SettingsPayload = components['schemas']['SettingsPayload'];
type TicketSettings = components['schemas']['TicketSettings'];
type PaperWidth = NonNullable<TicketSettings['paperWidth']>;
type PreviewKind = 'customer' | 'kitchen';

const SAMPLE_ITEMS = [
  { key: 1, priceKey: 'ticketPreviewSampleItem1', qty: 2, price: 45 },
  { key: 2, priceKey: 'ticketPreviewSampleItem2', qty: 1, price: 32 },
  { key: 3, priceKey: 'ticketPreviewSampleItem3', qty: 3, price: 12 },
] as const;

export const TicketSettings = () => {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();

  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  });

  const [draftTicket, setDraftTicket] = useState<Partial<TicketSettings> | undefined>(undefined);
  const [previewOpen, setPreviewOpen] = useState<PreviewKind | null>(null);

  const currentHeaderMessage = draftTicket?.headerMessage ?? settings?.ticket?.headerMessage ?? '';
  const currentFooterMessage = draftTicket?.footerMessage ?? settings?.ticket?.footerMessage ?? '';
  const currentPaperWidth: PaperWidth = draftTicket?.paperWidth ?? settings?.ticket?.paperWidth ?? 'MM_80';
  const currentShowTaxBreakdown = draftTicket?.showTaxBreakdown ?? settings?.ticket?.showTaxBreakdown ?? true;
  const currentShowTip = draftTicket?.showTip ?? settings?.ticket?.showTip ?? true;

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsPayload) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {
      setDraftTicket(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success('Configuración guardada con éxito');
    },
    onError: () => {
      toast.error('Error al guardar la configuración');
    }
  });

  const updateDraft = (patch: Partial<TicketSettings>) => {
    setDraftTicket({
      headerMessage: currentHeaderMessage,
      footerMessage: currentFooterMessage,
      paperWidth: currentPaperWidth,
      showTaxBreakdown: currentShowTaxBreakdown,
      showTip: currentShowTip,
      ...patch,
    });
  };

  const handleSave = () => {
    if (!settings) return;

    const payloadToSave: SettingsPayload = {
      ...settings,
      ticket: {
        headerMessage: currentHeaderMessage,
        footerMessage: currentFooterMessage,
        paperWidth: currentPaperWidth,
        showTaxBreakdown: currentShowTaxBreakdown,
        showTip: currentShowTip,
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftTicket(undefined);
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">{t('loadingSettingsLabel')}</div>;
  }

  const businessName = settings?.branding?.businessName || 'Ember';
  const ruc = settings?.branding?.ruc;
  const address = settings?.branding?.address;
  const phone = settings?.branding?.phone;
  const currencySymbol = settings?.billing?.currencySymbol ?? 'S/';
  const taxRules = settings?.billing?.taxRules ?? [];
  const tipPercentage = settings?.billing?.suggestedTipPercentage?.[0];

  const subtotal = SAMPLE_ITEMS.reduce((sum, item) => sum + item.qty * item.price, 0);
  const taxAmount = taxRules
    .filter((rule) => !rule.includedInPrice)
    .reduce((sum, rule) => sum + subtotal * ((rule.rate ?? 0) / 100), 0);
  const total = subtotal + taxAmount;
  const tipAmount = tipPercentage ? subtotal * (tipPercentage / 100) : 0;

  const paperWidthClass = currentPaperWidth === 'MM_58' ? 'max-w-[220px] text-[10px]' : 'max-w-[300px] text-xs';

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <FileText className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">{t('ticketCardTitle')}</CardTitle>
          <CardDescription>{t('ticketCardDescription')}</CardDescription>
        </div>
      </CardHeader>

      <CardContent className="space-y-6">
        <div className="max-w-md space-y-2">
          <Label htmlFor="headerMessage">{t('headerMessageLabel')}</Label>
          <Input
            id="headerMessage"
            value={currentHeaderMessage}
            placeholder={t('headerMessagePlaceholder')}
            onChange={(e) => updateDraft({ headerMessage: e.target.value })}
            className="focus-visible:ring-[#7a1315]"
          />
        </div>

        <div className="max-w-md space-y-2">
          <Label htmlFor="footerMessage">{t('footerMessageLabel')}</Label>
          <Input
            id="footerMessage"
            value={currentFooterMessage}
            placeholder={t('footerMessagePlaceholder')}
            onChange={(e) => updateDraft({ footerMessage: e.target.value })}
            className="focus-visible:ring-[#7a1315]"
          />
        </div>

        <div className="max-w-md space-y-2">
          <Label htmlFor="paperWidth">{t('paperWidthLabel')}</Label>
          <Select
            value={currentPaperWidth}
            onValueChange={(value) => updateDraft({ paperWidth: value as PaperWidth })}
          >
            <SelectTrigger id="paperWidth" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="MM_58">{t('paperWidth58Label')}</SelectItem>
              <SelectItem value="MM_80">{t('paperWidth80Label')}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex items-center justify-between max-w-md">
          <div className="space-y-0.5">
            <Label htmlFor="showTaxBreakdown">{t('showTaxBreakdownLabel')}</Label>
            <p className="text-xs text-muted-foreground">{t('showTaxBreakdownDescription')}</p>
          </div>
          <Switch
            id="showTaxBreakdown"
            checked={currentShowTaxBreakdown}
            onCheckedChange={(checked) => updateDraft({ showTaxBreakdown: checked })}
          />
        </div>

        <div className="flex items-center justify-between max-w-md">
          <div className="space-y-0.5">
            <Label htmlFor="showTip">{t('showTipLabel')}</Label>
            <p className="text-xs text-muted-foreground">{t('showTipDescription')}</p>
          </div>
          <Switch
            id="showTip"
            checked={currentShowTip}
            onCheckedChange={(checked) => updateDraft({ showTip: checked })}
          />
        </div>

        <div className="max-w-md space-y-2">
          <Label>{t('ticketPreviewLabel')}</Label>
          <div className="flex flex-wrap gap-3">
            <Button type="button" variant="outline" onClick={() => setPreviewOpen('customer')}>
              <Receipt className="mr-2 h-4 w-4" />
              {t('previewCustomerReceiptTab')}
            </Button>
            <Button type="button" variant="outline" onClick={() => setPreviewOpen('kitchen')}>
              <ChefHat className="mr-2 h-4 w-4" />
              {t('previewKitchenTicketTab')}
            </Button>
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftTicket === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          {t('undoChangesButton')}
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftTicket === undefined || updateSettingsMutation.isPending}
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

      <Dialog open={previewOpen !== null} onOpenChange={(open) => !open && setPreviewOpen(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {previewOpen === 'kitchen' ? t('previewKitchenTicketTab') : t('previewCustomerReceiptTab')}
            </DialogTitle>
          </DialogHeader>

          <div className="flex justify-center py-2">
            {previewOpen === 'customer' && (
              <div
                className={`${paperWidthClass} w-full font-mono bg-white text-zinc-900 border border-zinc-200 shadow-sm p-4 space-y-2`}
              >
                {currentHeaderMessage && (
                  <p className="text-center font-semibold whitespace-pre-wrap">{currentHeaderMessage}</p>
                )}
                <div className="text-center space-y-0.5">
                  <p className="font-bold">{businessName}</p>
                  {ruc && <p>RUC: {ruc}</p>}
                  {address && <p>{address}</p>}
                  {phone && <p>{phone}</p>}
                </div>
                <p className="border-t border-dashed border-zinc-300 pt-2">{t('ticketPreviewDateLabel')}</p>
                <p>{t('ticketPreviewTableLabel', { table: 5 })}</p>

                <div className="border-t border-dashed border-zinc-300 pt-2 space-y-1">
                  {SAMPLE_ITEMS.map((item) => (
                    <div key={item.key} className="flex justify-between gap-2">
                      <span>{item.qty}x {t(item.priceKey)}</span>
                      <span>{currencySymbol}{(item.qty * item.price).toFixed(2)}</span>
                    </div>
                  ))}
                </div>

                <div className="border-t border-dashed border-zinc-300 pt-2 space-y-1">
                  <div className="flex justify-between">
                    <span>{t('ticketPreviewSubtotalLabel')}</span>
                    <span>{currencySymbol}{subtotal.toFixed(2)}</span>
                  </div>
                  {currentShowTaxBreakdown && taxRules.map((rule, index) => (
                    <div key={index} className="flex justify-between text-zinc-500">
                      <span>{rule.name} ({rule.rate}%){rule.includedInPrice ? ` — ${t('taxIncludedLabel')}` : ''}</span>
                      {!rule.includedInPrice && (
                        <span>{currencySymbol}{(subtotal * ((rule.rate ?? 0) / 100)).toFixed(2)}</span>
                      )}
                    </div>
                  ))}
                  <div className="flex justify-between font-bold border-t border-dashed border-zinc-300 pt-1">
                    <span>{t('ticketPreviewTotalLabel')}</span>
                    <span>{currencySymbol}{total.toFixed(2)}</span>
                  </div>
                  {currentShowTip && tipPercentage != null && (
                    <div className="flex justify-between text-zinc-500">
                      <span>{t('ticketPreviewTipLabel', { percent: tipPercentage })}</span>
                      <span>{currencySymbol}{tipAmount.toFixed(2)}</span>
                    </div>
                  )}
                </div>

                {currentFooterMessage && (
                  <p className="text-center border-t border-dashed border-zinc-300 pt-2 whitespace-pre-wrap">{currentFooterMessage}</p>
                )}
              </div>
            )}

            {previewOpen === 'kitchen' && (
              <div
                className={`${paperWidthClass} w-full font-mono bg-white text-zinc-900 border border-zinc-200 shadow-sm p-4 space-y-2`}
              >
                {currentHeaderMessage && (
                  <p className="text-center font-semibold whitespace-pre-wrap">{currentHeaderMessage}</p>
                )}
                <p className="text-center font-bold">{t('ticketPreviewOrderLabel', { number: 128 })}</p>
                <p className="border-t border-dashed border-zinc-300 pt-2">{t('ticketPreviewDateLabel')}</p>
                <p>{t('ticketPreviewTableLabel', { table: 5 })}</p>

                <div className="border-t border-dashed border-zinc-300 pt-2 space-y-2">
                  {SAMPLE_ITEMS.map((item, index) => (
                    <div key={item.key}>
                      <p className="font-semibold">{item.qty}x {t(item.priceKey)}</p>
                      {index === 0 && (
                        <p className="text-zinc-500 pl-3">{t('ticketPreviewNotesLabel')}: {t('ticketPreviewSampleNote')}</p>
                      )}
                    </div>
                  ))}
                </div>

                {currentFooterMessage && (
                  <p className="text-center border-t border-dashed border-zinc-300 pt-2 whitespace-pre-wrap">{currentFooterMessage}</p>
                )}
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
};
