import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from '@/lib/i18n'

export const TenantSuspendedModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()
  const { t } = useTranslation('common')

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
            {t('tenantSuspendedTitle')}
          </DialogTitle>
          <DialogDescription>
            {detail || t('tenantSuspendedDefaultMessage')}
          </DialogDescription>
          <div className="flex gap-4 mt-6 flex-col">
            <Button onClick={handleLogout} className="w-full">
              {t('navLogout')}
            </Button>
          </div>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}
