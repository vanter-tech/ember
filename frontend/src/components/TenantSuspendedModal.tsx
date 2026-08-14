import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { useNavigate } from 'react-router-dom'

export const TenantSuspendedModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()

  const isOpen = activeModal === 'TENANT_SUSPENDED'
  const detail: string | undefined = modalPayload?.detail

  const handleLogout = () => {
    logout()
    closeModal()
    navigate('/login')
  }

  return (
    <Dialog open={isOpen} onOpenChange={() => {}}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6" showCloseButton={false}>
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            Cuenta suspendida
          </DialogTitle>
          <DialogDescription>
            {detail || 'Esta cuenta de restaurante no está activa. El acceso está bloqueado hasta que se resuelva la situación.'}
          </DialogDescription>
          <div className="flex gap-4 mt-6 flex-col">
            <Button onClick={handleLogout} className="w-full">
              Cerrar sesión
            </Button>
          </div>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}
