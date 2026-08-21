import { createFileRoute } from '@tanstack/react-router'
import { AuthLayout } from '@/app/auth-layout'
import { LoginForm } from '@/features/auth/components/login-form'

export const Route = createFileRoute('/login')({
  component: LoginRoute,
})

function LoginRoute() {
  return (
    <AuthLayout>
      <LoginForm />
    </AuthLayout>
  )
}
