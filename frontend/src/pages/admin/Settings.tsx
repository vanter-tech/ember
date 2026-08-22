import { useState } from "react";
import {useSettingsStore} from "@/store/uiStore";
import {SettingsBar} from "@/components/SettingsBar";
import { BrandingSettings } from "./components/settings/BrandingSettings";
import { SpacesSettings } from "./components/settings/SpaceSettings";
import { MenuSettings } from "./components/settings/MenuSettings";
import { BillingSettings } from "./components/settings/BillingSettings";
import { PaymentGatewaySettings } from "./components/settings/PaymentGatewaySettings";
import { BusinessHoursSettings } from "./components/settings/BusinessHoursSettings";
import { HardwareSettings } from "./components/settings/HardwareSettings";
import { LoyaltySettings } from "./components/settings/LoyaltySettings";
import { LoyaltyRewardsSettings } from "./components/settings/LoyaltyRewardsSettings";

export const Settings = () => {
    const { activeSettings } = useSettingsStore();
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

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

    return(
        <div className="p-6 flex flex-col md:flex-row gap-8">
            <div className={`w-full shrink-0 ${sidebarCollapsed ? 'md:w-fit' : 'md:w-64'}`}>
                <SettingsBar
                    collapsed={sidebarCollapsed}
                    onToggleCollapsed={() => setSidebarCollapsed((prev) => !prev)}
                />
            </div>
            <div className="flex-1 bg-white rounded-xl
            shadow-sm border border-zinc-200">
                {renderContent()}
            </div>
        </div>
    )
}
     