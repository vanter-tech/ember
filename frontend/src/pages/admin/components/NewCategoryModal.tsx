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
import { categoryService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

export const NewCategoryModal = () => {
  const { activeModal, closeModal } = useUIStore()
  const { t } = useTranslation('admin')

  const queryClient = useQueryClient()
  type CategoryFormInputs = z.infer<typeof categoryScheme>

  const categoryScheme = z.object({
    name: z.string().min(2, t('categoryNameMinLengthError')),
    description: z.string().min(10, t('categoryDescriptionMinLengthError')),
    image: z
      .any()
      .refine((file) => file instanceof File, t('imageRequiredError'))
      .refine((file) => file?.size <= 5 * 1024 * 1024, t('imageMaxSizeError')),
  })

  const form = useForm<CategoryFormInputs>({
    resolver: zodResolver(categoryScheme),
    defaultValues: {
      name: '',
      description: '',
      image: undefined,
    },
  })

  const mutation = useMutation({
    mutationFn: categoryService.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      toast.success(t('categoryCreatedToast'))
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error(t('genericErrorToast'))
    },
  })

  const onSubmit = (data: CategoryFormInputs) => {
    const formData = new FormData()
    formData.append('name', data.name)
    formData.append('description', data.description)
    formData.append('image', data.image)
    mutation.mutate(formData)
  }

  return (
    <Dialog
      open={activeModal == 'CREATE_CATEGORY'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-xl rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('newCategoryDialogTitle')}
          </DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
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
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('descriptionLabel')}</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder={t('newCategoryDescriptionPlaceholder')}
                      className="resize-none h-24 rounded-xl focus-visible:ring[#8c1717]"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="image"
              render={({ field: { value, onChange, ...fieldProps } }) => (
                <FormItem>
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

            <DialogFooter>
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
