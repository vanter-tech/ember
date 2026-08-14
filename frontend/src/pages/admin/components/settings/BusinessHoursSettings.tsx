import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SettingsService } from '@/lib/api';
import type { components } from '@/lib/backend-types';
import { Clock, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';

type SettingsPayload = components['schemas']['SettingsPayload'];
type DaySchedule = components['schemas']['DaySchedule'];
type DayOfWeek = NonNullable<DaySchedule['day']>;

const DAYS: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Lunes' },
  { value: 'TUESDAY', label: 'Martes' },
  { value: 'WEDNESDAY', label: 'Miércoles' },
  { value: 'THURSDAY', label: 'Jueves' },
  { value: 'FRIDAY', label: 'Viernes' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];

const defaultDaySchedule = (day: DayOfWeek): DaySchedule => ({
  day,
  closed: false,
  openTime: '09:00',
  closeTime: '18:00',
});

export const BusinessHoursSettings = () => {
  const queryClient = useQueryClient();

  const { data: settings, isPending: isLoadingSettings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  });

  const [draftSchedule, setDraftSchedule] = useState<DaySchedule[] | undefined>(undefined);

  const savedSchedule = settings?.businessHours?.schedule ?? [];
  const activeSchedule = draftSchedule ?? savedSchedule;

  const currentSchedule: DaySchedule[] = DAYS.map(
    ({ value }) => activeSchedule.find((entry) => entry.day === value) ?? defaultDaySchedule(value)
  );

  const updateSettingsMutation = useMutation({
    mutationFn: (updatedPayload: SettingsPayload) => SettingsService.updateSettings(updatedPayload),
    onSuccess: () => {
      setDraftSchedule(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] });
      toast.success("Configuración guardada con éxito");
    },
    onError: () => {
      toast.error("Error al guardar la configuración");
    }
  });

  const handleDayChange = (day: DayOfWeek, patch: Partial<DaySchedule>) => {
    setDraftSchedule(
      currentSchedule.map((entry) => (entry.day === day ? { ...entry, ...patch } : entry))
    );
  };

  const handleSave = () => {
    if (!settings) return;

    const payloadToSave: SettingsPayload = {
      ...settings,
      businessHours: {
        schedule: currentSchedule,
      }
    };

    updateSettingsMutation.mutate(payloadToSave);
  };

  const handleUndo = () => {
    setDraftSchedule(undefined);
  };

  if (isLoadingSettings) {
    return <div className="p-6 text-zinc-500">Cargando configuraciones...</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <Clock className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">Horario de Atención</CardTitle>
          <CardDescription>Define el horario semanal por día. Distinto del horario general de marca.</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-2xl space-y-4">
          {currentSchedule.map((entry) => {
            const day = entry.day as DayOfWeek;
            const label = DAYS.find((d) => d.value === day)?.label ?? day;
            const isClosed = entry.closed ?? false;

            return (
              <div
                key={day}
                className="flex flex-wrap items-center gap-4 border border-zinc-200 rounded-lg p-3"
              >
                <span className="w-24 shrink-0 font-medium text-sm text-zinc-700">{label}</span>

                <div className="flex items-center gap-2">
                  <Switch
                    id={`closed-${day}`}
                    checked={!isClosed}
                    onCheckedChange={(checked) => handleDayChange(day, { closed: !checked })}
                  />
                  <Label htmlFor={`closed-${day}`} className="text-xs">
                    {isClosed ? 'Cerrado' : 'Abierto'}
                  </Label>
                </div>

                <div className="flex items-center gap-2">
                  <Input
                    type="time"
                    value={entry.openTime ?? ''}
                    disabled={isClosed}
                    onChange={(e) => handleDayChange(day, { openTime: e.target.value })}
                    className="w-32 focus-visible:ring-[#7a1315]"
                  />
                  <span className="text-sm text-zinc-500">a</span>
                  <Input
                    type="time"
                    value={entry.closeTime ?? ''}
                    disabled={isClosed}
                    onChange={(e) => handleDayChange(day, { closeTime: e.target.value })}
                    className="w-32 focus-visible:ring-[#7a1315]"
                  />
                </div>
              </div>
            );
          })}
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftSchedule === undefined || updateSettingsMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          Deshacer cambios
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftSchedule === undefined || updateSettingsMutation.isPending}
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
