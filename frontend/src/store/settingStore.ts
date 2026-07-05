import {useQuery} from "@tanstack/react-query";
import { SettingsService } from "@/lib/api";

export const settingStore = () => {
    const { data: settings, isPending: isLoadingSettings } = useQuery({
        queryKey: ['restaurantSettings'],
        queryFn: () => SettingsService.getSettings(),
    })
    return { settings, isLoadingSettings }
}