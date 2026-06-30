import { Button } from "@/components/ui/button";
import { useUIStore } from "@/store/uiStore";
import { menuItemService } from "@/lib/api";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle
} from '@/components/ui/dialog'
import { useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";

export const DeleteMenuModal = () => {
    const {activeModal, modalPayload, closeModal} = useUIStore()
    const menuItemId = modalPayload?.id as number
    const queryClient = useQueryClient()

    const mutation = useMutation({
        mutationFn: menuItemService.delete,
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['menuItems']})
            toast.success('Menu eliminado.')
            closeModal()
        }
    })

    const handleDelete = () => {
        if(menuItemId) {
            mutation.mutate(menuItemId)
        }
    }

    return(
        <Dialog
        open={activeModal == 'DELETE_ITEMS'}
        >
            <DialogContent className="sm:max-w-md rounded-3xl p-6">
                <DialogHeader className="mb-4">
                    <DialogTitle className="m-auto text-2xl font-bold text-zinc-800">
                        ¿Estás seguro?
                    </DialogTitle>
                    <Button
                    onClick={closeModal}
                    variant={'outline'}
                    >
                        Cancelar
                    </Button>
                    <Button
                    onClick={handleDelete}
                    disabled={mutation.isPending}
                    >
                        {mutation.isPending ? 'Eliminando' : 'Si, Eliminar'}
                    </Button>
                </DialogHeader>
            </DialogContent>
        </Dialog>
    )
}