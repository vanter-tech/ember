import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { staffService } from '@/lib/api'
import { ROLE_LABELS } from '../types'

const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).+$/

const createStaffSchema = z.object({
  name: z.string().min(2, 'El nombre debe tener al menos 2 caracteres'),
  email: z.string().email('Correo inválido'),
  password: z
    .string()
    .min(8, 'La contraseña debe tener al menos 8 caracteres')
    .regex(
      PASSWORD_REGEX,
      'Debe incluir mayúscula, minúscula, número y carácter especial'
    ),
  role: z.enum(['WAITER', 'KITCHEN', 'ADMIN']),
})

type CreateStaffInputs = z.infer<typeof createStaffSchema>

export const CreateStaffModal = () => {
  const { activeModal, closeModal } = useUIStore()
  const queryClient = useQueryClient()

  const form = useForm<CreateStaffInputs>({
    resolver: zodResolver(createStaffSchema),
    defaultValues: { name: '', email: '', password: '', role: 'WAITER' },
  })

  const mutation = useMutation({
    mutationFn: staffService.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      toast.success('Empleado agregado.')
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo agregar el empleado.')
    },
  })

  const handleClose = () => {
    form.reset()
    closeModal()
  }

  return (
    <Dialog
      open={activeModal === 'CREATE_STAFF'}
      onOpenChange={(isOpen) => !isOpen && handleClose()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            Agregar empleado
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            Crea una cuenta de acceso para un nuevo miembro del equipo.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((data) => mutation.mutate(data))}
            className="space-y-5"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Nombre completo</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Ej. Ana Torres"
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Correo electrónico</FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      placeholder="ana@restaurante.com"
                      className="rounded-xl"
                      {...field}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Contraseña</FormLabel>
                  <FormControl>
                    <Input type="password" className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="role"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Rol</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="WAITER">{ROLE_LABELS.WAITER}</SelectItem>
                      <SelectItem value="KITCHEN">{ROLE_LABELS.KITCHEN}</SelectItem>
                      <SelectItem value="ADMIN">{ROLE_LABELS.ADMIN}</SelectItem>
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={handleClose}
                disabled={mutation.isPending}
              >
                Cancelar
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Guardando...' : 'Agregar empleado'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
