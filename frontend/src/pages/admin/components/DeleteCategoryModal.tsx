import { Button } from '../../../components/ui/button'
import { useUIStore } from '@/store/uiStore'
import { categoryService } from '@/lib/api'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '../../../components/ui/dialog'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

export const DeleteCategoryModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const categoyId = modalPayload as number
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: categoryService.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      toast.success('Categoria eliminada.')
      closeModal()
    },
  })

  const handleDelete = () => {
    if (categoyId) {
      mutation.mutate(categoyId)
    }
  }

  return (
    <Dialog
      open={activeModal == 'DELETE_CATEGORY'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="m-auto text-2xl font-bold text-zinc-800">
            ¿Estás seguro?
          </DialogTitle>
          <Button onClick={closeModal} variant={'outline'}>
            Cancelar
          </Button>
          <Button onClick={handleDelete} disabled={mutation.isPending}>
            {mutation.isPending ? 'Eliminando...' : 'Si, Eliminar'}
          </Button>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}
