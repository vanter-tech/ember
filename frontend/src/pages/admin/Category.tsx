
import { useQuery } from "@tanstack/react-query";
import {categoryService} from "@/lib/api";
import type {components} from '@/lib/backend-types'
import { Button } from '../../components/ui/button'
import { Bold, Pencil, Trash2 } from "lucide-react";

import { useUIStore } from "@/store/uiStore";

import { NewCategoryModal } from "@/components/NewCategoryModal";
import { DeleteCategoryModal } from "@/components/DeleteCategoryModal";
import { EditCategoryModal } from "@/components/EditCategoryModal";


export const Category = () => {

  const {openModal} = useUIStore()
  

  const {
    data: categories = [],
    isLoading,
    isError
  } = useQuery({
    queryKey: ['categories'],
    queryFn: categoryService.getAll
  });


  if( isLoading){
    return <div className="p-6 text-zinc-500">Cargando categorías de Ember...</div>;
  }

  if(isError){
    return <div className="p-6 text-red-500">Error al cargar las categorías.</div>;
  }


  return (
    <div className="p-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {categories.map((Category) => (
            <div key={Category.id} className="bg-white rounded-2xl shadow0-sm overflow-hidden border
            border-zinc-100 flex flex-col ">
              <div className="relative h-48 bg-zinc-200">
                <img
                src={Category.imgUrl || "https://via.placeholder.com/400"} 
                className="w-full h-full object-cover"
                />
                <span className="absolute top-4 right-4 bg-white/90 px-3 py-1 text-xs
                font-semibold text-green-700 rounded-full">
                  Activo
                </span>
              </div>

              <div className="p-5 pb-1 flex-1 flex flex-col">
                <div className="flex justify-between items-center mb-2">
                    <h3 className="text-xl font-bold text-zinc-800">{Category.name}</h3>
                  <div className="flex gap-2 text-zinc-400">
                    <Button variant="ghost" size="icon" className="hover-text-zinc-600 transition-colors"
                    onClick={() => openModal('EDIT_CATEGORY', Category)}>
                      <Pencil className="h-4 w-4"/>
                    </Button>
                    <Button variant="ghost" size="icon" className="hover-text-zinc-500 transition-colors"
                     onClick={() => openModal('DELETE_CATEGORY', Category.id)}>
                      <Trash2 className="h-4 w-4"/>
                    </Button>
                  </div>
                </div>
                
              </div>

              <p className="text-zinc-500 text-sm flex-1 m-4">
                {Category.description || "Placeholder, recuerda agregar esto al DTO en el backend"}
              </p>

              <div className="flex items-center p-4 border-t 
              border-zinc-100">
                <span className="bg-zinc-100 text-zinc-600 text-xs px-3 py-1 rounded-full
                font-medium">
                  12 Productos
                </span>
              </div>

            </div>
          ))}

        </div>

        
          <NewCategoryModal />
          <DeleteCategoryModal/>
          <EditCategoryModal/>
        

    </div>
  )
}