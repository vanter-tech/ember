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

const editStaffSchema = z.object({
  name: z.string().min(2, 'El nombre debe tener al menos 2 caracteres'),
  email: z.string().email('Correo inválido'),
  jobTitle: z.string(),
  shift: z.string(),
  contractType: z.string(),
  location: z.string(),
  active: z.boolean(),
})

type EditStaffInputs = z.infer<typeof editStaffSchema>

export const EditStaffModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const member = modalPayload as StaffMemberResponse | null

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
      toast.success('Empleado actualizado.')
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo actualizar el empleado.')
    },
  })

  return (
    <Dialog
      open={activeModal === 'EDIT_STAFF'}
      onOpenChange={(isOpen) => !isOpen && closeModal()}
    >
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            Editar empleado
          </DialogTitle>
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
                  <FormLabel>Correo electrónico</FormLabel>
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
                  <FormLabel>Puesto</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Ej. Mesera"
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
                  <FormLabel>Turno</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Ej. Mañana"
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
                  <FormLabel>Contrato</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Ej. Tiempo completo"
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
                  <FormLabel>Ubicación</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Ej. Sucursal Centro"
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
                <FormItem className="flex flex-row items-center justify-between gap-3 rounded-lg border p-4">
                  <FormLabel>Activo</FormLabel>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
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
                Cancelar
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Guardando...' : 'Guardar cambios'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
