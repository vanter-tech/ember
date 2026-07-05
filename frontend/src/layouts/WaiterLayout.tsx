import { FloatingNav } from "@/components/FloatingNav"
import { TopNav } from "@/components/TopNav"
import { Outlet } from "react-router-dom"

export const WaiterLayout = () => {
    return(
        <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
            <TopNav/>
            <main className="w-full">
                <Outlet/>
            </main>
            <FloatingNav/>
        </div>
    )
}