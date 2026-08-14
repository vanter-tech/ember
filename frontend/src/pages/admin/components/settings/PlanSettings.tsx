import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { restaurantAdminService, type UpdateRestaurantPlanRequest } from '@/lib/api';
import { CreditCard, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

type Plan = UpdateRestaurantPlanRequest['plan'];

const PLAN_OPTIONS: Plan[] = ['FREE', 'STARTER', 'PRO', 'ENTERPRISE'];

export const PlanSettings = () => {
  const queryClient = useQueryClient();

  const { data: restaurant, isPending: isLoadingRestaurant } = useQuery({
    queryKey: ['restaurantPlan'],
    queryFn: () => restaurantAdminService.getPlan(),
  });

  const [draftPlan, setDraftPlan] = useState<Plan | undefined>(undefined);

  const currentPlan = draftPlan ?? restaurant?.plan ?? 'FREE';

  const updatePlanMutation = useMutation({
    mutationFn: (plan: Plan) => restaurantAdminService.updatePlan(plan),
    onSuccess: () => {
      setDraftPlan(undefined);
      queryClient.invalidateQueries({ queryKey: ['restaurantPlan'] });
      toast.success('Plan actualizado con éxito');
    },
    onError: () => {
      toast.error('Error al actualizar el plan');
    },
  });

  const handleSave = () => {
    if (!draftPlan) return;
    updatePlanMutation.mutate(draftPlan);
  };

  const handleUndo = () => {
    setDraftPlan(undefined);
  };

  if (isLoadingRestaurant) {
    return <div className="p-6 text-zinc-500">Cargando plan...</div>;
  }

  return (
    <Card className="shadow-sm border-zinc-100">
      <CardHeader className="flex flex-row items-center gap-4 space-y-0 p-6">
        <div className="w-12 h-12 bg-red-50 text-[#7a1315] rounded-full flex items-center justify-center">
          <CreditCard className="w-6 h-6" />
        </div>
        <div>
          <CardTitle className="text-xl">Plan y Estado</CardTitle>
          <CardDescription>Consulta el estado de tu cuenta y cambia tu plan de suscripción.</CardDescription>
        </div>
      </CardHeader>

      <CardContent>
        <div className="max-w-md space-y-6">
          <div className="space-y-2">
            <Label>Estado de la cuenta</Label>
            <div>
              <Badge variant={restaurant?.status === 'ACTIVE' ? 'default' : 'destructive'}>
                {restaurant?.status ?? 'ACTIVE'}
              </Badge>
            </div>
          </div>

          <div className="space-y-3">
            <Label htmlFor="plan">Plan de suscripción</Label>
            <select
              id="plan"
              value={currentPlan}
              onChange={(e) => setDraftPlan(e.target.value as Plan)}
              className="h-12 w-full rounded-3xl border border-input bg-transparent px-2.5 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
            >
              {PLAN_OPTIONS.map((plan) => (
                <option key={plan} value={plan}>
                  {plan}
                </option>
              ))}
            </select>
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex justify-end gap-3 pt-6 border-t">
        <Button
          variant="outline"
          onClick={handleUndo}
          disabled={draftPlan === undefined || updatePlanMutation.isPending}
          className="text-zinc-700 hover:bg-zinc-100 p-4"
        >
          Deshacer cambios
        </Button>

        <Button
          onClick={handleSave}
          disabled={draftPlan === undefined || updatePlanMutation.isPending}
          className="hover:bg-[#b91016] text-white p-4"
        >
          {updatePlanMutation.isPending ? (
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
