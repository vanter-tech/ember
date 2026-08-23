import { useMemo } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
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
import { Switch } from '@/components/ui/switch'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { staffService, type StaffMemberResponse } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

const editStaffSchemaFactory = (t: ReturnType<typeof useTranslation<'admin'>>['t']) =>
  z.object({
    name: z.string().min(2, t('staffNameMinLengthError')),
    email: z.string().email(t('staffEmailInvalidError')),
    jobTitle: z.string(),
    shift: z.string(),
    contractType: z.string(),
    location: z.string(),
    active: z.boolean(),
  })

type EditStaffInputs = z.infer<ReturnType<typeof editStaffSchemaFactory>>

export const EditStaffModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const { t } = useTranslation('admin')
  const member = modalPayload as StaffMemberResponse | null
  const editStaffSchema = useMemo(() => editStaffSchemaFactory(t), [t])

  const form = useForm<EditStaffInputs>({
    resolver: zodResolver(editStaffSchema),
    values: {
      name: member?.name ?? '',
      email: member?.email ?? '',
      jobTitle: member?.jobTitle ?? '',
      shift: member?.shift ?? '',
      contractType: member?.contractType ?? '',
      location: member?.location ?? '',
      active: member?.active ?? true,
    },
  })

  const mutation = useMutation({
    mutationFn: (data: EditStaffInputs) => staffService.updateProfile(member!.id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.success(t('staffUpdatedToast'))
      closeModal()
    },
    onError: () => {
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
              name="jobTitle"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('jobTitleLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t('jobTitlePlaceholder')}
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
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
