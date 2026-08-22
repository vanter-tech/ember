import { useSettingsStore } from '@/store/uiStore'
import { Button } from './ui/button'
import {
  Store,
  Utensils,
  ConciergeBell,
  Receipt,
  CreditCard,
  Printer,
  Clock,
  Gift,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';
import { useTranslation } from '@/lib/i18n'

export const SettingsBar = ({
  collapsed,
  onToggleCollapsed,
}: {
  collapsed: boolean
  onToggleCollapsed: () => void
}) => {
    const { activeSettings, openSettings } = useSettingsStore()
    const { t } = useTranslation('admin')
    const billingGroupActive = activeSettings === 'BILLING' || activeSettings === 'PAYMENT_GATEWAY'

    return (
        <nav className={`flex flex-col gap-2 ${collapsed ? 'w-fit' : 'w-64'}`}>
            <Button
                variant={activeSettings === 'BRANDING' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('BRANDING')}
                className={collapsed ? 'justify-center px-2' : 'justify-start'}
                title={collapsed ? t('brandingAndBusinessLabel') : undefined}
            >
                <Store className={collapsed ? 'h-4 w-4' : 'mr-2 h-4 w-4'} />
                {!collapsed && t('brandingAndBusinessLabel')}
            </Button>
            <Button
                variant={activeSettings === 'MENU' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('MENU')}
                className={collapsed ? 'justify-center px-2' : 'justify-start'}
                title={collapsed ? t('menuLabel') : undefined}
            >
                <Utensils className={collapsed ? 'h-4 w-4' : 'mr-2 h-4 w-4'} />
                {!collapsed && t('menuLabel')}
            </Button>

            <Button
                variant={billingGroupActive ? 'default' : 'ghost'}
                className={collapsed ? 'justify-center px-2' : 'justify-start'}
                title={collapsed ? t('billingLabel') : undefined}
                onClick={collapsed ? () => openSettings('BILLING') : undefined}
            >
                <Receipt className={collapsed ? 'h-4 w-4' : 'mr-2 h-4 w-4'} />
                {!collapsed && t('billingLabel')}
            </Button>
            {!collapsed && (
                <div className="flex flex-col gap-1 pl-6">
                    <Button
                        variant={activeSettings === 'BILLING' ? 'destructive' : 'ghost'}
                        size="sm"
                        onClick={() => openSettings('BILLING')}
                        className="justify-start"
                    >
                        <Receipt className="mr-2 h-4 w-4" />
                        {t('billingLabel')}
                    </Button>
                    <Button
                        variant={activeSettings === 'PAYMENT_GATEWAY' ? 'destructive' : 'ghost'}
                        size="sm"
                        onClick={() => openSettings('PAYMENT_GATEWAY')}
                        className="justify-start"
                    >
                        <CreditCard className="mr-2 h-4 w-4" />
                        {t('paymentGatewayCardTitle')}
                    </Button>
                </div>
            )}

            <Button
                variant={activeSettings === 'HARDWARE' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('HARDWARE')}
                className={collapsed ? 'justify-center px-2' : 'justify-start'}
                title={collapsed ? t('hardwareLabel') : undefined}
            >
                <Printer className={collapsed ? 'h-4 w-4' : 'mr-2 h-4 w-4'} />
                {!collapsed && t('hardwareLabel')}
            </Button>
            <Button
                variant={activeSettings === 'SPACE' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('SPACE')}
                className={collapsed ? 'justify-center px-2' : 'justify-start'}
                title={collapsed ? t('spaceLabel') : undefined}
            >
                <ConciergeBell className={collapsed ? 'h-4 w-4' : 'mr-2 h-4 w-4'} />
                {!collapsed && t('spaceLabel')}
            </Button>
            <Button
                variant={activeSettings === 'HORARIO' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('HORARIO')}
                className={collapsed ? 'justify-center px-2' : 'justify-start'}
                title={collapsed ? t('scheduleLabel') : undefined}
            >
                <Clock className={collapsed ? 'h-4 w-4' : 'mr-2 h-4 w-4'} />
                {!collapsed && t('scheduleLabel')}
            </Button>
            <Button
                variant={activeSettings === 'FIDELIZACION' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('FIDELIZACION')}
                className={collapsed ? 'justify-center px-2' : 'justify-start'}
                title={collapsed ? t('loyaltyLabel') : undefined}
            >
                <Gift className={collapsed ? 'h-4 w-4' : 'mr-2 h-4 w-4'} />
                {!collapsed && t('loyaltyLabel')}
            </Button>

            <Button
                variant="ghost"
                size={collapsed ? 'icon' : 'default'}
                onClick={onToggleCollapsed}
                className="fixed left-6 bottom-4 sm:bottom-8 z-50 rounded-full bg-white dark:bg-zinc-900 shadow-2xl border border-zinc-200 dark:border-zinc-800"
                title={collapsed ? t('expandSidebarLabel') : t('collapseSidebarLabel')}
            >
                {collapsed ? <PanelLeftOpen className="h-6 w-6" /> : <PanelLeftClose className="mr-2 h-6 w-6" />}
                {!collapsed && t('collapseSidebarLabel')}
            </Button>
        </nav>
    )
}
