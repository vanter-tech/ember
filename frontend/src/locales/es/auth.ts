import type { auth as enAuth } from '../en/auth'

export const auth = {
  loginTagline: 'Inicia sesión para continuar.',
  loginDescription: 'Ingresa tu correo y contraseña para acceder a tu cuenta.',
  emailPlaceholder: 'Ingresa tu correo electrónico',
  passwordPlaceholder: 'Ingresa tu contraseña',
  loggingIn: 'Iniciando sesión...',
  login: 'Iniciar sesión',
  registerLink: 'Regístrate',
  registerTitle: 'Registro',
  registerDescription: 'Crea una cuenta para comenzar.',
  namePlaceholder: 'Tu nombre',
  registerEmailPlaceholder: 'Tu correo electrónico',
  registerPasswordPlaceholder: 'Tu contraseña',
  registerSubmit: 'Registrarse',
  loginLink: '¿Ya tienes una cuenta? Inicia sesión',
} satisfies typeof enAuth
