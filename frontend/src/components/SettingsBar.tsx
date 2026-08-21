import { useSettingsStore } from '@/store/uiStore'
import { Button } from './ui/button'
import {
  Store,
  Utensils,
  ConciergeBell,
  Receipt,
  Printer,
  Clock,
  Gift,
} from 'lucide-react';
import { useTranslation } from '@/lib/i18n'

export const SettingsBar = () => {
    const { activeSettings, openSettings } = useSettingsStore()
    const { t } = useTranslation('admin')
    return (
        <nav className="flex flex-col gap-2 w-64">
            <Button
                variant={activeSettings === 'BRANDING' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('BRANDING')}
                className="justify-start"    
            >
                <Store className="mr-2 h-4 w-4" />
                {t('brandingAndBusinessLabel')}
            </Button>
            <Button
                variant={activeSettings === 'MENU' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('MENU')}
                className="justify-start"
            >
                <Utensils className="mr-2 h-4 w-4" />
                {t('menuLabel')}
            </Button>
            <Button
                variant={activeSettings === 'BILLING' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('BILLING')}
                className="justify-start"       
            >
                <Receipt className="mr-2 h-4 w-4" />
                {t('billingLabel')}
            </Button>
            <Button
                variant={activeSettings === 'HARDWARE' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('HARDWARE')}   
                className="justify-start"
            >
                <Printer className="mr-2 h-4 w-4" />
                {t('hardwareLabel')}
            </Button>
            <Button
                variant={activeSettings === 'SPACE' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('SPACE')}
                className="justify-start"
            >
                <ConciergeBell className="mr-2 h-4 w-4" />
                {t('spaceLabel')}
            </Button>
            <Button
                variant={activeSettings === 'HORARIO' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('HORARIO')}
                className="justify-start"
            >
                <Clock className="mr-2 h-4 w-4" />
                {t('scheduleLabel')}
            </Button>
            <Button
                variant={activeSettings === 'FIDELIZACION' ?
                    'default' : 'ghost'}
                onClick={() => openSettings('FIDELIZACION')}
                className="justify-start"
            >
                <Gift className="mr-2 h-4 w-4" />
                {t('loyaltyLabel')}
            </Button>
        </nav>
    )


}
