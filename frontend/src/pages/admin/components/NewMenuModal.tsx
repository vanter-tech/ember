import { Button } from '../../../components/ui/button'
import { Input } from '../../../components/ui/input'
import { Textarea } from '../../../components/ui/textarea'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'

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
import { menuItemService } from '@/lib/api'
import { Switch } from '@/components/ui/switch'
import { useTranslation } from '@/lib/i18n'

export const NewMenuModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const { t } = useTranslation('admin')
  type menuItemsFormInputs = z.infer<typeof menuItemScheme>

  const menuItemScheme = z.object({
    name: z.string().min(2, 'Namer must have at least 2 characters'),
    descriptions: z.string().min(10, 'Type your description here'),
    price: z.number().min(0, "Type the product's price"),
    available: z.boolean(),
    image: z
      .any()
      .refine((file) => file instanceof File, 'You must choose an image')
      .refine(
        (file) => file?.size <= 5 * 1024 * 1024,
        'Size should be 5MB MAX'
      ),
    categoryId: z.number().int(),
  })

  const form = useForm<menuItemsFormInputs>({
    resolver: zodResolver(menuItemScheme),
    defaultValues: {
      name: '',
      descriptions: '',
      price: 0,
      available: true,
      image: undefined,
      categoryId: 0,
    },
  })

  const mutation = useMutation({
    mutationFn: menuItemService.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] })
      toast.success('Items successful created!.')
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error('An ERROR has occurred')
    },
  })

  const onSubmit = (data: menuItemsFormInputs) => {
    const formData = new FormData()
    formData.append('name', data.name)
    formData.append('description', data.descriptions)
    formData.append('price', String(data.price))
    formData.append('image', data.image)
    formData.append('available', String(data.available))
    formData.append('categoryId', String(modalPayload?.id))
    mutation.mutate(formData)
  }

  return (
    <Dialog
      open={activeModal == 'CREATE_ITEMS'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-xl rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('newDishDialogTitle')}
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
                      placeholder={t('dishNamePlaceholder')}
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
              name="descriptions"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>{t('descriptionLabel')}</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder={t('dishNamePlaceholder')}
                      className="resize-none h-24 rounded-xl focus-visible:ring[#8c1717]"
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
