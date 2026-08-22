import { useQuery } from '@tanstack/react-query';
import { loyaltyRewardService } from '@/lib/api';
import { Pencil, Plus } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useUIStore } from '@/store/uiStore';
import { CreateRewardModal } from './loyalty/CreateRewardModal';
import { EditRewardModal } from './loyalty/EditRewardModal';
import { TIER_BADGE_CLASSNAMES, TIER_LABELS } from './loyalty/types';
import { useTranslation } from '@/lib/i18n';

export const LoyaltyRewardsSettings = () => {
  const { t } = useTranslation('admin');
  const openModal = useUIStore((state) => state.openModal);

  const { data: rewards, isPending: isLoadingRewards } = useQuery({
    queryKey: ['loyaltyRewards'],
    queryFn: loyaltyRewardService.list,
  });

  return (
    <>
      <Card className="shadow-sm border-zinc-100">
        <CardHeader className="flex flex-row items-center justify-between gap-4 space-y-0 p-6">
          <div>
            <CardTitle className="text-xl">{t('rewardCatalogTitle')}</CardTitle>
            <CardDescription>{t('rewardCatalogDescription')}</CardDescription>
          </div>
          <Button
            onClick={() => openModal('CREATE_REWARD')}
            className="hover:bg-[#b91016] text-white"
          >
            <Plus className="mr-2 h-4 w-4" />
            {t('newRewardButton')}
          </Button>
        </CardHeader>

        <CardContent>
          {isLoadingRewards ? (
            <div className="p-6 text-zinc-500">{t('loadingRewards')}</div>
          ) : !rewards || rewards.length === 0 ? (
            <div className="flex items-center justify-center rounded-xl border border-dashed border-border py-12 text-sm text-muted-foreground">
              {t('noRewardsYet')}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('nameLabel')}</TableHead>
                  <TableHead>{t('requiredTierLabel')}</TableHead>
                  <TableHead>{t('statusColumnLabel')}</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rewards.map((reward) => (
                  <TableRow key={reward.id}>
                    <TableCell>
                      <div className="font-medium text-zinc-800">{reward.name}</div>
                      {reward.description && (
                        <div className="text-xs text-muted-foreground">{reward.description}</div>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge className={TIER_BADGE_CLASSNAMES[reward.requiredTier!]}>
                        {TIER_LABELS[reward.requiredTier!]}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={reward.active ? 'default' : 'outline'}>
                        {reward.active ? t('activeRewardLabel') : t('inactiveRewardLabel')}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openModal('EDIT_REWARD', reward)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <CreateRewardModal />
      <EditRewardModal />
    </>
  );
};
