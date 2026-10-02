import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useUIStore } from '@/store/uiStore'
import { useAuthStore } from '@/store/authStore'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { staffService, type StaffMemberResponse, type StaffRole } from '@/lib/api'
import { ROLE_LABELS } from '../types'
import { useTranslation } from '@/lib/i18n'
import { formatDateTime } from '@/lib/format'

const editStaffSchemaFactory = (t: ReturnType<typeof useTranslation<'admin'>>['t']) =>
  z.object({
    name: z.string().min(2, t('staffNameMinLengthError')),
    email: z.string().email(t('staffEmailInvalidError')),
    role: z.enum(['WAITER', 'KITCHEN', 'ADMIN', 'ACCOUNTANT']),
    shift: z.string(),
    contractType: z.string(),
    location: z.string(),
    active: z.boolean(),
  })

type EditStaffInputs = z.infer<ReturnType<typeof editStaffSchemaFactory>>

/**
 * Admin-only quick-login PIN control for a staff account. Lives inside the edit modal ("each
 * account's config") and is the ONLY place a PIN can be assigned — the old self-service flow in
 * the layout headers was removed. Writes go straight through, independent of the profile form.
 */
const StaffPinSection = ({
  userId,
  hasPin,
  pinUpdatedAt,
}: {
  userId: string
  hasPin: boolean
  pinUpdatedAt?: string
}) => {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [pin, setPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reset = () => {
    setPin('')
    setConfirmPin('')
    setError(null)
  }

  const setMutation = useMutation({
    mutationFn: () => staffService.setPin(userId, pin),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.success(t('staffPinSavedToast'))
      reset()
    },
    onError: () => toast.error(t('staffPinErrorToast')),
  })

  const clearMutation = useMutation({
    mutationFn: () => staffService.clearPin(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.success(t('staffPinRemovedToast'))
      reset()
    },
    onError: () => toast.error(t('staffPinErrorToast')),
  })

  const busy = setMutation.isPending || clearMutation.isPending

  const submit = () => {
    if (!/^\d{4,6}$/.test(pin)) {
      setError(t('staffPinFormatError'))
      return
    }
    if (pin !== confirmPin) {
      setError(t('staffPinMismatchError'))
      return
    }
    setError(null)
    setMutation.mutate()
  }

  return (
    <div className="sm:col-span-2 flex flex-col gap-3 rounded-lg border p-4">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-medium">{t('staffPinSectionTitle')}</span>
        <span
          className={`rounded-full px-2 py-0.5 text-xs ${
            hasPin ? 'bg-emerald-100 text-emerald-700' : 'bg-zinc-100 text-zinc-500'
          }`}
        >
          {hasPin ? t('staffPinSetStatus') : t('staffPinNoneStatus')}
        </span>
      </div>
      {hasPin && pinUpdatedAt && (
        <p className="text-xs text-muted-foreground">{t('staffPinUpdatedAt', { time: formatDateTime(pinUpdatedAt) })}</p>
      )}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Input
          type="text"
          inputMode="numeric"
          maxLength={6}
          className="rounded-xl"
          placeholder={t('staffPinNewLabel')}
          aria-label={t('staffPinNewLabel')}
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
        />
        <Input
          type="text"
          inputMode="numeric"
          maxLength={6}
          className="rounded-xl"
          placeholder={t('staffPinConfirmLabel')}
          aria-label={t('staffPinConfirmLabel')}
          value={confirmPin}
          onChange={(e) => setConfirmPin(e.target.value.replace(/\D/g, ''))}
        />
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="flex items-center gap-2">
        <Button type="button" size="sm" onClick={submit} disabled={busy || !pin}>
          {hasPin ? t('staffPinUpdateButton') : t('staffPinAddButton')}
        </Button>
        {hasPin && (
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => clearMutation.mutate()}
            disabled={busy}
          >
            {t('staffPinRemoveButton')}
          </Button>
        )}
      </div>
    </div>
  )
}

// Same rule the backend enforces (CreateStaffRequest / AdminResetPasswordRequest).
const PASSWORD_POLICY = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).{8,128}$/

/**
 * Admin-only password reset for a non-admin account. The password is NOT temporary: the admin
 * picks it and tells it to the person (their open sessions are signed out server-side). The
 * backend allows one reset per account every 6 hours, so the button is disabled until
 * `availableAt` and says when it opens again.
 */
const StaffPasswordSection = ({
  userId,
  availableAt,
}: {
  userId: string
  availableAt?: string
}) => {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Captured on mount (no impure call during render); the modal is short-lived and `availableAt` only
  // moves forward after a reset, so it cannot go stale in a way that unlocks the button early.
  const [now] = useState(() => Date.now())
  const lockedUntil = availableAt && new Date(availableAt).getTime() > now ? new Date(availableAt) : null

  const mutation = useMutation({
    mutationFn: () => staffService.resetPassword(userId, password),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.success(t('staffPasswordSavedToast'))
      setPassword('')
      setConfirm('')
      setError(null)
    },
    onError: () => {
      // A 429 (cooldown) or a rejected password both land here; refresh so the button reflects the server.
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.error(t('staffPasswordErrorToast'))
    },
  })

  const submit = () => {
    if (!PASSWORD_POLICY.test(password)) {
      setError(t('staffPasswordPolicyError'))
      return
    }
    if (password !== confirm) {
      setError(t('staffPasswordMismatchError'))
      return
    }
    setError(null)
    mutation.mutate()
  }

  return (
    <div className="sm:col-span-2 flex flex-col gap-3 rounded-lg border p-4">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-medium">{t('staffPasswordSectionTitle')}</span>
        {lockedUntil && (
          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
            {t('staffPasswordCooldownStatus', {
              time: lockedUntil.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
            })}
          </span>
        )}
      </div>
      <p className="text-xs text-muted-foreground">{t('staffPasswordHint')}</p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Input
          type="password"
          autoComplete="new-password"
          className="rounded-xl"
          placeholder={t('staffPasswordNewLabel')}
          aria-label={t('staffPasswordNewLabel')}
          value={password}
          disabled={!!lockedUntil}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Input
          type="password"
          autoComplete="new-password"
          className="rounded-xl"
          placeholder={t('staffPasswordConfirmLabel')}
          aria-label={t('staffPasswordConfirmLabel')}
          value={confirm}
          disabled={!!lockedUntil}
          onChange={(e) => setConfirm(e.target.value)}
        />
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div>
        <Button
          type="button"
          size="sm"
          onClick={submit}
          disabled={mutation.isPending || !!lockedUntil || !password}
        >
          {t('staffPasswordResetButton')}
        </Button>
      </div>
    </div>
  )
}

export const EditStaffModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const { t } = useTranslation('admin')
  const currentUserId = useAuthStore((state) => state.userId)
  const modalMember = modalPayload as StaffMemberResponse | null
  // QA_SIMULATION_REPORT.md E-13: `modalPayload` is a one-time snapshot of the staff row taken
  // when "Profile" was clicked. Saving a PIN below invalidates the `['staff']` query and refetches
  // it, but that snapshot never updates — the modal kept showing "No PIN" even after a successful
  // save, until it was closed and reopened. Subscribing to the same cache (without triggering our
  // own fetch — Staff.tsx already owns that) and reading the live row by id keeps this in sync.
  const { data: staffList } = useQuery({
    queryKey: ['staff'],
    queryFn: staffService.getAll,
    enabled: false,
  })
  const member = staffList?.find((s) => s.id === modalMember?.id) ?? modalMember
  const editStaffSchema = useMemo(() => editStaffSchemaFactory(t), [t])

  // Guard against an admin locking themselves out of the panel by demoting their own account.
  const isSelf = !!member?.id && member.id === currentUserId

  const initialRole: StaffRole =
    member?.role && member.role !== 'CUSTOMER' ? member.role : 'WAITER'

  const form = useForm<EditStaffInputs>({
    resolver: zodResolver(editStaffSchema),
    values: {
      name: member?.name ?? '',
      email: member?.email ?? '',
      role: initialRole,
      shift: member?.shift ?? '',
      contractType: member?.contractType ?? '',
      location: member?.location ?? '',
      active: member?.active ?? true,
    },
  })

  const mutation = useMutation({
    mutationFn: async (data: EditStaffInputs) => {
      const { role, ...profile } = data
      await staffService.updateProfile(member!.id!, profile)
      if (role !== member?.role) {
        await staffService.updateRole(member!.id!, role)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.success(t('staffUpdatedToast'))
      closeModal()
    },
    onError: () => {
      // Profile and role are two requests: one may already be applied (a role change can even be
      // saved while its response fails). Re-read the server state so the modal never shows a stale row.
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.error(t('staffUpdateErrorToast'))
    },
  })

  return (
    <Dialog
      open={activeModal === 'EDIT_STAFF'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-xl rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('editEmployeeTitle')}
          </DialogTitle>
          <DialogDescription>{t('editEmployeeDescription')}</DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((data) => mutation.mutate(data))}
            className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-5"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('fullNameLabel')}</FormLabel>
                  <FormControl>
                    <Input className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('emailLabel')}</FormLabel>
                  <FormControl>
                    <Input type="email" className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="role"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('roleLabel')}</FormLabel>
                  <Select
                    value={field.value}
                    onValueChange={field.onChange}
                    disabled={isSelf}
                  >
                    <FormControl>
                      <SelectTrigger className="w-full rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="WAITER">{ROLE_LABELS.WAITER}</SelectItem>
                      <SelectItem value="KITCHEN">{ROLE_LABELS.KITCHEN}</SelectItem>
                      <SelectItem value="ACCOUNTANT">{ROLE_LABELS.ACCOUNTANT}</SelectItem>
                      <SelectItem value="ADMIN">{ROLE_LABELS.ADMIN}</SelectItem>
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="shift"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('shiftLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t('shiftPlaceholder')}
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="contractType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('contractTypeLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t('contractTypePlaceholder')}
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="location"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('locationLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t('locationPlaceholder')}
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="active"
              render={({ field }) => (
                <FormItem className="sm:col-span-2 flex flex-row items-center justify-between gap-3 rounded-lg border p-4">
                  <FormLabel>{t('activeStatus')}</FormLabel>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            {member?.id && (
              <StaffPinSection userId={member.id} hasPin={member.hasPin ?? false} pinUpdatedAt={member.pinUpdatedAt} />
            )}

            {member?.id && member.role !== 'ADMIN' && (
              <StaffPasswordSection userId={member.id} availableAt={member.passwordResetAvailableAt} />
            )}

            <DialogFooter className="sm:col-span-2">
              <Button
                type="button"
                variant="outline"
                onClick={closeModal}
                disabled={mutation.isPending}
              >
                {t('cancelButton')}
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? t('savingEllipsisLabel') : t('saveChangesButton')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
