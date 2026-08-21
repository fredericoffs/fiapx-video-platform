import { createFileRoute } from '@tanstack/react-router'
import { AuthLayout } from '@/app/auth-layout'
import { RegisterForm } from '@/features/auth/components/register-form'

export const Route = createFileRoute('/register')({
  component: RegisterRoute,
})

function RegisterRoute() {
  return (
    <AuthLayout>
      <RegisterForm />
    </AuthLayout>
  )
}
