import {useSettingsStore} from "@/store/uiStore";
import {SettingsBar} from "@/components/SettingsBar";
import { BrandingSettings } from "./components/settings/BrandingSettings";
import { SpacesSettings } from "./components/settings/SpaceSettings";
import { MenuSettings } from "./components/settings/MenuSettings";

export const Settings = () => {
    const { activeSettings } = useSettingsStore();

    const renderContent = () => {
        switch (activeSettings) {
            case 'BRANDING':
                return <BrandingSettings />;
            case 'MENU':
                return <MenuSettings />;
            case 'BILLING':
                return <div>Billing Settings</div>;
            case 'HARDWARE':
                return <div>Hardware Settings</div>;
            case 'SPACE':
                return <SpacesSettings />;
        }
    };

    return(
        <div className="p-6 flex flex-col md:flex-row gap-8">
            <div className="w-full md:w-64 shrink-0">
                <SettingsBar />
            </div>
            <div className="flex-1 bg-white rounded-xl
            shadow-sm border border-zinc-200">
                {renderContent()}
            </div>
        </div>
    )
}
     