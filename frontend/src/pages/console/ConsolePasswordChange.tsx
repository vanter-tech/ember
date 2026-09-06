import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import toast from 'react-hot-toast'
import { platformAuthService } from '@/lib/platformApi'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'

const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).+$/

const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1, 'La contraseña actual es obligatoria'),
  newPassword: z
    .string()
    .min(8, 'La contraseña debe tener entre 8 y 128 caracteres')
    .max(128, 'La contraseña debe tener entre 8 y 128 caracteres')
    .regex(passwordPattern, 'La contraseña necesita mayúscula, minúscula, número y símbolo'),
})

type PasswordChangeFormInputs = z.infer<typeof passwordChangeSchema>

export default function ConsolePasswordChange() {
  const form = useForm<PasswordChangeFormInputs>({
    resolver: zodResolver(passwordChangeSchema),
    defaultValues: {
      currentPassword: '',
      newPassword: '',
    },
  })

  const changePassword = useMutation({
    mutationFn: (data: PasswordChangeFormInputs) => platformAuthService.changePassword(data),
    onSuccess: () => {
      toast.success('Contraseña actualizada')
      form.reset()
    },
    onError: (error) => {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        toast.error('La contraseña actual es incorrecta', {
          id: 'console-password-error',
          duration: 3000,
        })
      } else {
        toast.error('No se pudo actualizar la contraseña', {
          id: 'console-password-error',
          duration: 3000,
        })
      }
    },
  })

  const onSubmit = (data: PasswordChangeFormInputs) => changePassword.mutate(data)

  return (
    <div className="flex flex-col gap-4">
      <ConsolePageHeader title="Cambiar contraseña" />
      <Link to="/console" className="text-sm text-[#8c1717] hover:underline">
        &larr; Panel
      </Link>

      <Card className="max-w-md">
        <CardContent className="pt-6">
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
              <FormField
                control={form.control}
                name="currentPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Contraseña actual</FormLabel>
                    <FormControl>
                      <Input
                        type="password"
                        autoComplete="current-password"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="newPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Nueva contraseña</FormLabel>
                    <FormControl>
                      <Input
                        type="password"
                        autoComplete="new-password"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <Button
                type="submit"
                className="w-full"
                disabled={changePassword.isPending}
              >
                {changePassword.isPending ? 'Actualizando...' : 'Actualizar contraseña'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
