import { Button } from "@/components/ui/button"
import {useSessionStore} from "@/store/sessionStore"
import { ArrowRight } from "lucide-react"

export const ItemsFloatingIsland = () => {

    const items = useSessionStore((state) => state.items  || [])
    if(!items || items.length === 0) return null
    const previewItems = items.slice(0, 3)
    const remainingCount = items.length - 2

    return(
        <div className="flex items-center gap-4 bg-white p-2 pr-2 rounded-full shadow-[0_3px_15px_rgba(0,0,0,0.1)] border border-gray-50">
            <div className="flex items-center ml-2">
                <div className="flex -space-x-2">
                    {previewItems.map((item,index) => (
                        <div key={index} className="w-10 h-10 rounded-full border-2 border-white bg-gray-800
                        flex items-center justify-centerr text-xs text-white">
                            Foto
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
                {items.length} platos seleccionados
            </span>
            <Button className="px-5 rounded-full text-sm font-semibold flex items-center gap-2">
                Ver Comanda
                <ArrowRight />
            </Button>
        </div>
    )
}