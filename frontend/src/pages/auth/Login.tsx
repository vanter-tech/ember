import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Link } from 'react-router-dom'
import axios from 'axios'
import toast from 'react-hot-toast'
import { useAuthStore } from '../../store/authStore'
import {
  useQuickAccessStore,
  type QuickAccessProfile,
} from '@/store/quickAccessStore'
import { authService } from '@/lib/api'
import { navigateForRole } from './navigateForRole'
import { QuickLoginModal } from './QuickLoginModal'
import { AuthBackground } from './AuthBackground'
import { PoweredByVanter } from './PoweredByVanter'
import { QuickStartCarousel } from './QuickStartCarousel'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { useTranslation } from '@/lib/i18n'
import { isHubBuild } from '@/lib/isHubBuild'

import { Button } from '../../components/ui/button'
import { Input } from '../../components/ui/input'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '../../components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from '../../components/ui/form'

const createLoginSchema = (t: ReturnType<typeof useTranslation<'auth'>>['t']) =>
  z.object({
    email: z.string().email(t('invalidEmail')).min(1, t('emailRequired')),
    password: z.string().min(6, t('passwordTooShort')),
  })

type LoginFormInputs = z.infer<ReturnType<typeof createLoginSchema>>

export const Login = () => {
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()
  const { t: tAuth } = useTranslation('auth')
  const loginSchema = useMemo(() => createLoginSchema(tAuth), [tAuth])

  const { profiles, forget, remember } = useQuickAccessStore()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(false)
  const [activeChip, setActiveChip] = useState<QuickAccessProfile | null>(null)
  const chipsVisible = profiles.length > 0 && !showForm

  const form = useForm<LoginFormInputs>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  })

  const onSubmit = async (data: LoginFormInputs) => {
    try {
      const response = await authService.login(data)
      setAuth(response)
      remember({
        email: data.email,
        name: response.name ?? data.email,
        role: response.role ?? '',
      })
      toast.success(tAuth('loginSuccessToast'))
      await navigateForRole(response, navigate, { tAuth })
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        toast.error(tAuth('unauthorizedToast'), {
          id: 'login-error',
          duration: 3000,
        })
      } else if (axios.isAxiosError(error) && error.response?.status === 429) {
        toast.error(tAuth('tooManyLoginAttemptsToast'), {
          id: 'login-error',
          duration: 3000,
        })
      } else {
        toast.error(tAuth('loginFailedToast'), {
          id: 'login-error',
          duration: 3000,
        })
      }
    }
  }

  return (
    <div className="relative flex items-center justify-center min-h-screen overflow-hidden bg-white p-4 pb-12">
      <AuthBackground />
      {chipsVisible ? (
        <div className="relative z-10 flex w-full max-w-5xl flex-col items-center gap-8">
          <div className="absolute -top-2 right-0">
            <LanguageSwitcher />
          </div>
          <div className="text-center">
            <h1 className="text-4xl font-bold text-[#920703]">
              {tAuth('loginTagline')}
            </h1>
            <p className="mt-2 text-sm text-zinc-500">{tAuth('loginDescription')}</p>
          </div>
          <div className="flex w-full flex-col gap-4">
            <div className="flex items-center justify-between px-2">
              <span className="text-base font-semibold text-zinc-700">
                {tAuth('quickStartTitle')}
              </span>
              <button
                type="button"
                className="cursor-pointer text-sm text-zinc-500 hover:underline"
                onClick={() => setEditing((e) => !e)}
              >
                {editing ? tAuth('doneEditingChips') : tAuth('editChips')}
              </button>
            </div>
            <QuickStartCarousel itemCount={profiles.length}>
              {profiles.map((p) => (
                <div key={p.email} className="relative shrink-0 snap-center">
                  <button
                    type="button"
                    onClick={() => setActiveChip(p)}
                    className="flex h-44 w-44 flex-col items-center justify-center gap-3 rounded-3xl border bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                  >
                    <span
                      className="flex h-20 w-20 items-center justify-center rounded-full text-white text-2xl font-bold"
                      style={{ backgroundColor: `hsl(${p.colorSeed} 55% 45%)` }}
                    >
                      {p.initials}
                    </span>
                    <span className="flex w-full flex-col items-center text-center">
                      <span className="w-full text-base font-medium text-zinc-800 truncate">
                        {p.name}
                      </span>
                      <span className="text-[11px] uppercase tracking-wide text-zinc-400">
                        {p.role}
                      </span>
                    </span>
                  </button>
                  {editing && (
                    <button
                      type="button"
                      aria-label={tAuth('removeChipAria', { name: p.name })}
                      onClick={() => forget(p.email)}
                      className="absolute right-2 top-2 h-6 w-6 rounded-full cursor-pointer bg-zinc-700 text-white text-sm"
                    >
                      ×
                    </button>
                  )}
                </div>
              ))}
            </QuickStartCarousel>
            <button
              type="button"
              className="mx-auto cursor-pointer text-sm text-zinc-600 hover:underline"
              onClick={() => setShowForm(true)}
            >
              {tAuth('useAnotherAccount')}
            </button>
          </div>
        </div>
      ) : (
      <Card className="w-full max-w-md gap-6 py-10 shadow-lg relative z-10">
        <div className="absolute top-4 right-4">
          <LanguageSwitcher />
        </div>
        <CardHeader className="px-8">
          <CardTitle className="text-3xl text-center text-[#920703] font-bold">
            {tAuth('loginTagline')}
          </CardTitle>
          <CardDescription className="text-center">
            {tAuth('loginDescription')}
          </CardDescription>
        </CardHeader>
        <CardContent className="px-8">
          <Form {...form}>
            <form
              onSubmit={form.handleSubmit(onSubmit)}
              className="space-y-6"
            >
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <div className="relative">
                        <svg
                          viewBox="0 0 24 24"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                          className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
                        >
                          <g id="SVGRepo_bgCarrier" strokeWidth="0"></g>
                          <g
                            id="SVGRepo_tracerCarrier"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          ></g>
                          <g id="SVGRepo_iconCarrier">
                            {' '}
                            <path
                              d="M5 21C5 17.134 8.13401 14 12 14C15.866 14 19 17.134 19 21M16 7C16 9.20914 14.2091 11 12 11C9.79086 11 8 9.20914 8 7C8 4.79086 9.79086 3 12 3C14.2091 3 16 4.79086 16 7Z"
                              stroke="#b7b7b7ff"
                              strokeWidth="2"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                            ></path>{' '}
                          </g>
                        </svg>

                        <Input
                          placeholder={tAuth('emailPlaceholder')}
                          {...field}
                          className="pl-10"
                          autoComplete="email"
                        />
                      </div>
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
                      <div className="relative">
                        <svg
                          viewBox="0 0 24 24"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                          className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
                        >
                          <g id="SVGRepo_bgCarrier" strokeWidth="0"></g>
                          <g
                            id="SVGRepo_tracerCarrier"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          ></g>
                          <g id="SVGRepo_iconCarrier">
                            {' '}
                            <path
                              d="M12 14.5V16.5M7 10.0288C7.47142 10 8.05259 10 8.8 10H15.2C15.9474 10 16.5286 10 17 10.0288M7 10.0288C6.41168 10.0647 5.99429 10.1455 5.63803 10.327C5.07354 10.6146 4.6146 11.0735 4.32698 11.638C4 12.2798 4 13.1198 4 14.8V16.2C4 17.8802 4 18.7202 4.32698 19.362C4.6146 19.9265 5.07354 20.3854 5.63803 20.673C6.27976 21 7.11984 21 8.8 21H15.2C16.8802 21 17.7202 21 18.362 20.673C18.9265 20.3854 19.3854 19.9265 19.673 19.362C20 18.7202 20 17.8802 20 16.2V14.8C20 13.1198 20 12.2798 19.673 11.638C19.3854 11.0735 18.9265 10.6146 18.362 10.327C18.0057 10.1455 17.5883 10.0647 17 10.0288M7 10.0288V8C7 5.23858 9.23858 3 12 3C14.7614 3 17 5.23858 17 8V10.0288"
                              stroke="#b7b7b7ff"
                              strokeWidth="2"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                            ></path>{' '}
                          </g>
                        </svg>
                        <Input
                          className="pl-10"
                          placeholder={tAuth('passwordPlaceholder')}
                          type="password"
                          autoComplete="current-password"
                          {...field}
                        />
                      </div>
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
                {form.formState.isSubmitting ? tAuth('loggingIn') : tAuth('login')}
              </Button>

              {!isHubBuild() && (
                <Button asChild variant="outline" className="w-full text-center mb-3">
                  <Link to="/register">{tAuth('registerLink')}</Link>
                </Button>
              )}
            </form>
          </Form>
        </CardContent>
      </Card>
      )}
      {activeChip && (
        <QuickLoginModal profile={activeChip} onClose={() => setActiveChip(null)} />
      )}
      <PoweredByVanter />
    </div>
  )
}
