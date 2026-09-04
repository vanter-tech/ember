import { Button } from "@/components/ui/button"
import {useSessionStore} from "@/store/sessionStore"
import { ArrowRight } from "lucide-react"
import { useNavigate } from "react-router-dom"
import { useAuthStore } from "@/store/authStore"
import { useTranslation } from "@/lib/i18n"
import type { orderItemDTO } from "@/lib/api"

// QA_SIMULATION_REPORT.md E-03: a `|| []` literal inside the selector allocates a brand-new
// array every render whenever `items` is undefined (e.g. on first load before any session has
// been set), so Zustand/useSyncExternalStore sees a "changed" snapshot every time and re-renders
// forever ("Maximum update depth exceeded"). A module-level constant keeps the fallback reference
// stable across renders.
const EMPTY_ITEMS: orderItemDTO[] = []

export const ItemsFloatingIsland = () => {
    const navigate = useNavigate()
    const items = useSessionStore((state) => state.items ?? EMPTY_ITEMS)
    const tableId = useSessionStore((state) => state.tableId)
    const currentId = useAuthStore((state) => state.userId)
    const { t } = useTranslation('customer')

    const myFilterItems = items.filter((item) => item.participantId === currentId)

    if(!myFilterItems || myFilterItems.length === 0) return null
    const previewItems = myFilterItems.slice(0, 3)
    const remainingCount = myFilterItems.length - 3

    return(
        <div className="flex items-center gap-4 bg-white p-2 pr-2 rounded-full shadow-[0_3px_15px_rgba(0,0,0,0.1)] border border-gray-50">
            <div className="flex items-center ml-2">
                <div className="flex -space-x-2">
                    {previewItems.map((_item,index) => (
                        <div key={index} className="w-10 h-10 rounded-full border-2 border-white bg-gray-800
                        flex items-center justify-centerr text-xs text-white">
                            {t('itemsIslandPhotoPlaceholder')}
                        </div>
                    ))}
                </div>
                {remainingCount > 0 && (
                    <div className="w-10 h-10 rounded-full border-2 border-white bg-gray-200 flex 
                    items-center justify-center text-sm font-semibold text-gray-600 -ml-3 z-10">
                        +{remainingCount}
                    </div>
                )}
            </div>
            <span className="text-sm font-medium text-gray-600">
                {t('itemsIslandSelectedCount', { count: myFilterItems.length })}
            </span>

            <Button className="px-5 rounded-full text-sm font-semibold flex items-center gap-2"
            onClick={()=> {
                navigate(`${tableId}/comanda`)
            }}
            >
                {t('itemsIslandViewComanda')}
                <ArrowRight />
            </Button>
        </div>
    )
}