import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import toast from 'react-hot-toast'
import { usePlatformAuthStore } from '@/store/platformAuthStore'
import { platformAuthService } from '@/lib/platformApi'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from '@/components/ui/form'

const loginSchema = z.object({
  email: z.string().email('Email inválido').min(1, 'El email es obligatorio'),
  password: z.string().min(1, 'La contraseña es obligatoria'),
})

type LoginFormInputs = z.infer<typeof loginSchema>

export default function ConsoleLogin() {
  const navigate = useNavigate()
  const { setAuth } = usePlatformAuthStore()

  const form = useForm<LoginFormInputs>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  })

  const onSubmit = async (data: LoginFormInputs) => {
    try {
      const response = await platformAuthService.login(data)
      setAuth(response)
      toast.success('Sesión iniciada')
      navigate('/console', { replace: true })
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        toast.error('Credenciales inválidas', { id: 'console-login-error', duration: 3000 })
      } else if (axios.isAxiosError(error) && error.response?.status === 429) {
        toast.error('Demasiados intentos. Probá de nuevo en un rato.', {
          id: 'console-login-error',
          duration: 3000,
        })
      } else {
        toast.error('No se pudo iniciar sesión', { id: 'console-login-error', duration: 3000 })
      }
    }
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-50">
      <div className="mb-6 text-center text-2xl font-bold text-[#8c1717]">Ember</div>
      <Card className="w-full max-w-md shadow-lg">
        <CardHeader>
          <CardTitle className="text-3xl text-center text-[#8c1717] font-bold">
            Consola de operadores
          </CardTitle>
          <CardDescription className="text-center">
            Ingresá con tu cuenta de operador.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <Input
                        placeholder="Ingresá tu email"
                        {...field}
                        autoComplete="email"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <Input
                        placeholder="Ingresá tu contraseña"
                        type="password"
                        autoComplete="current-password"
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
                disabled={form.formState.isSubmitting}
              >
                {form.formState.isSubmitting ? 'Ingresando...' : 'Iniciar sesión'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
