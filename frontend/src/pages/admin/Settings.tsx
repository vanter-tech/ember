import { useState } from "react";
import {useSettingsStore, type SettingsType} from "@/store/uiStore";
import {SettingsBar} from "@/components/SettingsBar";
import { SectionTour } from "@/components/tours/SectionTour";
import { useTranslation } from "@/lib/i18n";
import { dictionaries } from "@/locales";
import { BrandingSettings } from "./components/settings/BrandingSettings";
import { SpacesSettings } from "./components/settings/SpaceSettings";
import { MenuSettings } from "./components/settings/MenuSettings";
import { BillingSettings } from "./components/settings/BillingSettings";
import { PaymentGatewaySettings } from "./components/settings/PaymentGatewaySettings";
import { TicketSettings } from "./components/settings/TicketSettings";
import { PrintingSettings } from "./components/settings/PrintingSettings";
import { BusinessHoursSettings } from "./components/settings/BusinessHoursSettings";
import { HardwareSettings } from "./components/settings/HardwareSettings";
import { LoyaltySettings } from "./components/settings/LoyaltySettings";
import { LoyaltyRewardsSettings } from "./components/settings/LoyaltyRewardsSettings";

// Every tab's tour is a single step against the shared #settings-tour-content pane (the tab
// switch is local state, not a route, so there's no per-tab element to add stable ids to without
// touching all 11 settings components) — BRANDING's tour additionally leads with a sidebar-
// navigation step since it's the tab a fresh admin lands on first.
type AdminTranslationKey = keyof (typeof dictionaries)['es']['admin']

const TAB_TOUR_KEYS: Record<
    Exclude<SettingsType, null>,
    { title: AdminTranslationKey; content: AdminTranslationKey }
> = {
    BRANDING: { title: 'tourSettingsBrandingTitle', content: 'tourSettingsBrandingContent' },
    MENU: { title: 'tourSettingsMenuTitle', content: 'tourSettingsMenuContent' },
    BILLING: { title: 'tourSettingsBillingTitle', content: 'tourSettingsBillingContent' },
    PAYMENT_GATEWAY: { title: 'tourSettingsPaymentGatewayTitle', content: 'tourSettingsPaymentGatewayContent' },
    TICKET: { title: 'tourSettingsTicketTitle', content: 'tourSettingsTicketContent' },
    PRINTING: { title: 'tourSettingsPrintingTitle', content: 'tourSettingsPrintingContent' },
    HARDWARE: { title: 'tourSettingsHardwareTitle', content: 'tourSettingsHardwareContent' },
    SPACE: { title: 'tourSettingsSpaceTitle', content: 'tourSettingsSpaceContent' },
    HORARIO: { title: 'tourSettingsHorarioTitle', content: 'tourSettingsHorarioContent' },
    FIDELIZACION: { title: 'tourSettingsFidelizacionTitle', content: 'tourSettingsFidelizacionContent' },
    LOYALTY_REWARDS: { title: 'tourSettingsLoyaltyRewardsTitle', content: 'tourSettingsLoyaltyRewardsContent' },
}

export const Settings = () => {
    const { activeSettings } = useSettingsStore();
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const { t } = useTranslation('admin');

    const renderContent = () => {
        switch (activeSettings) {
            case 'BRANDING':
                return <BrandingSettings />;
            case 'MENU':
                return <MenuSettings />;
            case 'BILLING':
                return <BillingSettings />;
            case 'PAYMENT_GATEWAY':
                return <PaymentGatewaySettings />;
            case 'TICKET':
                return <TicketSettings />;
            case 'PRINTING':
                return <PrintingSettings />;
            case 'HARDWARE':
                return <HardwareSettings />;
            case 'SPACE':
                return <SpacesSettings />;
            case 'HORARIO':
                return <BusinessHoursSettings />;
            case 'FIDELIZACION':
                return <LoyaltySettings />;
            case 'LOYALTY_REWARDS':
                return <LoyaltyRewardsSettings />;
        }
    };

    const tourSteps = activeSettings
        ? [
              ...(activeSettings === 'BRANDING'
                  ? [
                        {
                            target: '#settings-tour-sidebar',
                            title: t('tourSettingsSidebarTitle'),
                            content: t('tourSettingsSidebarContent'),
                            skipBeacon: true,
                        },
                    ]
                  : []),
              {
                  target: '#settings-tour-content',
                  title: t(TAB_TOUR_KEYS[activeSettings].title),
                  content: t(TAB_TOUR_KEYS[activeSettings].content),
                  skipBeacon: activeSettings !== 'BRANDING',
              },
          ]
        : [];

    return(
        <div className="p-6 flex flex-col md:flex-row gap-4 md:gap-8">
            <div className={`w-full shrink-0 ${sidebarCollapsed ? 'md:w-fit' : 'md:w-64'}`}>
                <SettingsBar
                    collapsed={sidebarCollapsed}
                    onToggleCollapsed={() => setSidebarCollapsed((prev) => !prev)}
                />
            </div>
            <div id="settings-tour-content" className="flex-1 bg-white rounded-xl
            shadow-sm border border-zinc-200">
                {renderContent()}
            </div>
            {activeSettings && (
                <SectionTour
                    key={activeSettings}
                    sectionId={`admin-settings-${activeSettings.toLowerCase()}`}
                    steps={tourSteps}
                />
            )}
        </div>
    )
}
