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
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'

const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).+$/

const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1, 'Current password is required'),
  newPassword: z
    .string()
    .min(8, 'Password must be between 8 and 128 characters')
    .max(128, 'Password must be between 8 and 128 characters')
    .regex(
      passwordPattern,
      'Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character'
    ),
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
      toast.success('Password updated')
      form.reset()
    },
    onError: (error) => {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        toast.error('Current password is incorrect', {
          id: 'console-password-error',
          duration: 3000,
        })
      } else {
        toast.error('Failed to update password', {
          id: 'console-password-error',
          duration: 3000,
        })
      }
    },
  })

  const onSubmit = (data: PasswordChangeFormInputs) => changePassword.mutate(data)

  return (
    <div className="flex flex-col gap-4">
      <Link to="/console" className="text-sm text-blue-600 hover:underline">
        &larr; Panel
      </Link>

      <Card className="max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl">Cambiar contraseña</CardTitle>
        </CardHeader>
        <CardContent>
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
