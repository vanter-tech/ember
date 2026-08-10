import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { categoryService, SessionTableService } from '@/lib/api' 
import { useUIStore } from '@/store/uiStore'
import { Button } from '@/components/ui/button' 

export const GlobalDeleteModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()

  const isDeleteModal = activeModal === 'DELETE_CATEGORY' || activeModal === 'DELETE_ITEMS'

  const mutation = useMutation({
    mutationFn: async () => {
      if (activeModal === 'DELETE_CATEGORY') {
        return categoryService.delete(modalPayload as number)
      }
      if (activeModal === 'DELETE_ITEMS') {
        return SessionTableService.deleteItem(modalPayload.sessionId, modalPayload.itemId)
      }
    },
    onSuccess: () => {
      if (activeModal === 'DELETE_CATEGORY') {
        queryClient.invalidateQueries({ queryKey: ['categories'] })
        toast.success('Categoría eliminada.')
      }
      if (activeModal === 'DELETE_ITEMS') {
        queryClient.invalidateQueries({ queryKey: ['sessionDetails'] }) 
        toast.success('Platillo eliminado.')
      }
      closeModal()
    },
    onError: () => {
      toast.error('Error al eliminar el registro.')
    }
  })

  const handleDelete = () => {
    mutation.mutate()
  }

  return (
    <Dialog 
       open={isDeleteModal} 
       onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="m-auto text-2xl font-bold text-zinc-800">
            ¿Estás seguro?
          </DialogTitle>
          <div className="flex gap-4 mt-6 flex-col">
            <Button onClick={closeModal} variant="outline" className="w-full">
              Cancelar
            </Button>
            <Button 
              onClick={handleDelete} 
              disabled={mutation.isPending} 
              className="w-full"
            >
              {mutation.isPending ? 'Eliminando...' : 'Sí, Eliminar'}
            </Button>
          </div>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}