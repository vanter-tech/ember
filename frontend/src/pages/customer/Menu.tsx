import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { menuServices } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import toast from 'react-hot-toast'
import { Button } from '@/components/ui/button'
import { Card, CardDescription, CardTitle } from '@/components/ui/card'
import { useUIStore } from '@/store/uiStore'
import { useEffect, useState } from 'react'
import { number } from 'zod'
import { settingStore } from '@/store/settingStore'
import { ArrowLeft } from 'lucide-react'

export const Menu = () => {
  const { settings } = settingStore()
  const [activeCategory, setActiveCategory] = useState<Number | undefined>()
  const {
    data: menuItems = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['digital-menu'],
    queryFn: () => menuServices.getMenu(),
  })
  const itemsCategory =
    menuItems.find((item) => item.id == activeCategory)?.items || []

  useEffect(() => {
    if (menuItems.length > 0 && activeCategory === undefined) {
      setActiveCategory(menuItems[0].id)
    }
  }, [menuItems, activeCategory])

  if (isLoading)
    return <div className="p-6 text-zinc-500">Cargando platillos...</div>
  if (isError)
    return (
      <div className="p-6 text-red-500">Error al cargar los platillos.</div>
    )

  return (
    <>
      <div className="p-2">
        <div className="flex items-center w-full h-20 justify-items-start shadow-sm rounded-3xl p-4 gap-4">
          <Button className=" h-13 w-13 rounded-full hover:bg-gray-200">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1
            className="text-3xl font-bold
                text-[#8c1717]"
            tracking-tight
          >
            Ember
          </h1>
        </div>
        <div className="flex flex-col gap-4 p-4">
          <div className="flex items-center justify-between p-4">
            <div className=" flex flex-col">
              <h1 className="text-3xl font-bold">Carta Digital</h1>
              <p className="text-sm text-gray-500 mt-1">
                Explora nuestra seleccion gourmet para hoy.
              </p>
            </div>
            <Badge className="p-6 text-md font-bold">Menu de almuerzo</Badge>
          </div>
          <div className='flex flex-row gap-3 p-2 pb-5 border-b overflow-x-auto'>
            {menuItems.map((categories) => (
              <div className={`w-auto shadow-sm p-4 rounded-3xl cursor-pointer hover:bg-[#8c1717]
              shrink-0 ${activeCategory === categories.id ? 'bg-[#8c1717] text-white' : 'bg-white text-[#8c1717]'}`}
                onClick={() => setActiveCategory(categories.id)} key={categories.id}>
                {categories.name}
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  )
}
