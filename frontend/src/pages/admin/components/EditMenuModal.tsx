import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'react-hot-toast'
import { useState, useEffect } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form'

import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { menuItemService, modifierGroupService } from '@/lib/api'
import { Switch } from '@/components/ui/switch'
import { useTranslation } from '@/lib/i18n'
import { ModifierGroupAssignmentField, type ModifierGroupAssignment } from './ModifierGroupAssignmentField'

export const EditMenuModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const { t } = useTranslation('admin')
  const [modifierGroups, setModifierGroups] = useState<ModifierGroupAssignment[]>([])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- seed editable field-array state from the modal payload when the dialog opens
    setModifierGroups(
      (modalPayload?.modifierGroups ?? []).map((g: { id: number }, index: number) => ({
        groupId: g.id,
        displayOrder: index,
      }))
    )
  }, [modalPayload])

  type menuItemsFormInputs = z.infer<typeof menuItemScheme>

  const menuItemScheme = z.object({
    name: z.string().min(2, t('dishNameMinLengthError')),
    description: z.string().min(10, t('dishDescriptionMinLengthError')),
    price: z.number().min(0, t('dishPriceRequiredError')),
    available: z.boolean(),
    image: z
      .any()
      .refine((file) => file instanceof File, t('imageRequiredError'))
      .refine((file) => file?.size <= 5 * 1024 * 1024, t('imageMaxSizeError'))
      .optional(),
    categoryId: z.number().int(),
  })

  const form = useForm<menuItemsFormInputs>({
    resolver: zodResolver(menuItemScheme),
    defaultValues: {
      name: '',
      description: '',
      price: 0,
      available: true,
      image: undefined,
      categoryId: 0,
    },
    values: {
      name: modalPayload?.name,
      description: modalPayload?.description,
      price: modalPayload?.price,
      available: modalPayload?.available,
      image: modalPayload?.image,
      categoryId: modalPayload?.category?.id,
    },
  })

  const mutation = useMutation({
    mutationFn: ({ id, formData }: { id: number; formData: FormData }) => {
      return menuItemService.update(id, formData)
    },
    onSuccess: async () => {
      await modifierGroupService.assignToMenuItem(modalPayload.id, modifierGroups)
      queryClient.invalidateQueries({ queryKey: ['menuItems'] })
      toast.success(t('menuItemUpdatedToast'))
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error(t('genericErrorToast'))
    },
  })

  const onSubmit = (data: menuItemsFormInputs) => {
    const formData = new FormData()
    formData.append('name', data.name)
    formData.append('description', data.description)
    formData.append('price', String(data.price))
    formData.append('available', String(data.available))
    if (data.image) {
      formData.append('image', data.image)
    }
    formData.append('categoryId', String(data.categoryId))
    mutation.mutate({
      id: modalPayload.id,
      formData: formData,
    })
  }

  return (
    <Dialog
      open={activeModal == 'EDIT_ITEMS'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-xl rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('editDishTitle')}
          </DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-5"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('nameLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t('categoryNamePlaceholder')}
                      className="rounded-xl focus-visible:ring[#8c1717]"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="price"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('priceLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t('pricePlaceholder')}
                      className="rounded-xl focus-visible:ring[#8c1717]"
                      {...field}
                      onChange={(e) => field.onChange(Number(e.target.value))}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>{t('descriptionLabel')}</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder={t('descriptionPlaceholder')}
                      className="rounded-xl focus-visible:ring[#8c1717]"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="available"
              render={({ field }) => (
                <FormItem className="sm:col-span-2 flex flex-row items-center gap-3 rounded-lg border p-4">
                  <FormLabel>{t('activateLabel')}</FormLabel>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      className="rounded-xl focus-visible:ring[#8c1717]"
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="image"
              render={({ field: { value, onChange, ...fieldProps } }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>{t('coverImageLabel')}</FormLabel>
                  <FormControl>
                    <Input
                      type="file"
                      accept="image/*"
                      placeholder={t('coverImageLabel')}
                      className="rounded-xl file:text-[#8c1717] file:font-semibold
                                                                    hover:file:cursor-pointer cursor-pointer"
                      onChange={(e) => {
                        const file = e.target.files?.[0]
                        if (file) onChange(file)
                      }}
                      {...fieldProps}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <ModifierGroupAssignmentField value={modifierGroups} onChange={setModifierGroups} />

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
                {mutation.isPending ? t('savingButton') : t('saveButton')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
