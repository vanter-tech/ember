import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm, useFieldArray } from 'react-hook-form'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { modifierGroupService, type ModifierGroupRequest } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'

export const NewModifierGroupModal = () => {
  const { activeModal, closeModal } = useUIStore()
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const groupScheme = z.object({
    name: z.string().min(2, t('modifierGroupNameMinLengthError')),
    selectionType: z.enum(['SINGLE_REQUIRED', 'MULTI_OPTIONAL', 'MULTI_LIMITED']),
    minSelections: z.number().optional(),
    maxSelections: z.number().optional(),
    options: z.array(z.object({
      name: z.string().min(1),
      priceDelta: z.number().min(0),
    })).min(1),
  })
  type GroupFormInputs = z.infer<typeof groupScheme>

  const form = useForm<GroupFormInputs>({
    resolver: zodResolver(groupScheme),
    defaultValues: {
      name: '',
      selectionType: 'SINGLE_REQUIRED',
      minSelections: undefined,
      maxSelections: undefined,
      options: [{ name: '', priceDelta: 0 }],
    },
  })

  const { fields, append } = useFieldArray({ control: form.control, name: 'options' })
  const selectionType = form.watch('selectionType')

  const mutation = useMutation({
    mutationFn: (request: ModifierGroupRequest) => modifierGroupService.create(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modifierGroups'] })
      toast.success(t('modifierGroupCreatedToast'))
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error(t('genericErrorToast'))
    },
  })

  const onSubmit = (data: GroupFormInputs) => {
    mutation.mutate({
      name: data.name,
      selectionType: data.selectionType,
      minSelections: data.selectionType === 'MULTI_LIMITED' ? data.minSelections : undefined,
      maxSelections: data.selectionType === 'MULTI_LIMITED' ? data.maxSelections : undefined,
      options: data.options,
    })
  }

  return (
    <Dialog open={activeModal == 'CREATE_MODIFIER_GROUP'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-xl rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('newModifierGroupDialogTitle')}
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
                    <Input className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="selectionType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('selectionTypeLabel')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="SINGLE_REQUIRED">{t('selectionTypeSingleRequired')}</SelectItem>
                      <SelectItem value="MULTI_OPTIONAL">{t('selectionTypeMultiOptional')}</SelectItem>
                      <SelectItem value="MULTI_LIMITED">{t('selectionTypeMultiLimited')}</SelectItem>
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />

            {selectionType === 'MULTI_LIMITED' && (
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={form.control}
                  name="minSelections"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('minSelectionsLabel')}</FormLabel>
                      <FormControl>
                        <Input type="number" className="rounded-xl" value={field.value ?? ''}
                          onChange={(e) => field.onChange(Number(e.target.value))} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="maxSelections"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('maxSelectionsLabel')}</FormLabel>
                      <FormControl>
                        <Input type="number" className="rounded-xl" value={field.value ?? ''}
                          onChange={(e) => field.onChange(Number(e.target.value))} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            )}

            <div className="space-y-3">
              <FormLabel>{t('optionsLabel')}</FormLabel>
              {fields.map((option, index) => (
                <div key={option.id} className="flex gap-3">
                  <FormField
                    control={form.control}
                    name={`options.${index}.name`}
                    render={({ field }) => (
                      <Input placeholder={t('optionNamePlaceholder')} className="rounded-xl flex-1" {...field} />
                    )}
                  />
                  <FormField
                    control={form.control}
                    name={`options.${index}.priceDelta`}
                    render={({ field }) => (
                      <Input
                        type="number"
                        placeholder={t('priceDeltaPlaceholder')}
                        className="rounded-xl w-32"
                        {...field}
                        onChange={(e) => field.onChange(Number(e.target.value))}
                      />
                    )}
                  />
                </div>
              ))}
              <Button type="button" variant="outline" onClick={() => append({ name: '', priceDelta: 0 })}>
                {t('addOptionButton')}
              </Button>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
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
